/*
 * Copyright 2019 Stas Levin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.staslev.codedistillery

import java.nio.file.{Path, Paths}

import com.staslev.codedistillery.vcs.SourceControlledRepo
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.{Random, Try}

object LocalSparkParallelism {
  implicit def spark: Parallelism[SparkContext] = LocalSparkCluster

  private object LocalSparkCluster extends Parallelism[SparkContext] {
    val model: SparkContext =
      new SparkContext(
        new SparkConf()
          .setMaster(s"local[*]")
          .setAppName("CodeDistillery")
          .set("spark.driver.memory", "10g")
          .set("spark.executor.memory", "4g"))

  }
}

object Helper {
  def projectName(repoPath: Path): String = {
    repoPath.getName(repoPath.getNameCount - 1).toString
  }

  def prependProjectName(repoPath: Path, values: Iterable[String]): Iterable[String] = {
    values.map(str => Helper.projectName(repoPath) + CSVUtils.separator + str)
  }

  def revisionsWithParent(repo: SourceControlledRepo, branch: String): List[String] = {
    repo
      .revisions(branch)
      .toList
      .reverse
      .drop(1)
  }
}

/**
  * An execution model where repositories and their revisions are processed sequentially.
  */
trait SequentialExecution extends ExecutionModel[SparkContext] {
  override def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {
    val encoder = encoderFactory()
    val writer = new DistillOutputWriter(encoder, output)
    repoBranches.foreach({
      case (repoPath, branch) =>
        val repo = vcsFactory(repoPath)
        val distiller = distillerFactory.apply(repo)

        // revisions are listed from recent to oldest
        // skip first commit on account of the big bang theory
        Helper
          .revisionsWithParent(repo, branch)
          .foreach(revision =>
            writer.write(distiller.distillCommit(revision), Helper.projectName(repoPath)))
    })
    writer.close()
  }
}

/**
  * An execution model where repositories are processed in parallel, but within each repository,
  * the revisions are processed sequentially.
  */
trait InterRepoParallelism extends ExecutionModel[SparkContext] {
  override def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {
    parallelism.model
      .parallelize(repoBranches.toList.map({ case (path, branch) => (path.toString, branch) }))
      .flatMap({
        case (repoPathString, branch) =>
          val repoPath = Paths.get(repoPathString)
          val repo = vcsFactory(repoPath)
          val distiller = distillerFactory.apply(repo)
          val encoder = encoderFactory()
          // revisions are listed from recent to oldest
          // skip first commit on account of the big bang theory
          Helper
            .revisionsWithParent(repo, branch)
            .flatMap(revision => {
              val values = encoder.toCSV(distiller.distillCommit(revision))
              Helper.prependProjectName(repoPath, values)
            })
      })
      .saveAsTextFile(output.toString)
  }
}

/**
  * An execution model where repositories are processed sequentially, but the revisions of each
  * repository are processed in parallel. This execution model clones the repository being
  * processed is cloned per core, and assigns it a range of revisions, representing its slice
  * of the entire revisions history (of the repository currently being processed).
  */
trait RepoCloningLocalRevisionParallel extends ExecutionModel[SparkContext] {
  override def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {
    val sparkContext = parallelism.model

    repoBranches.foreach({
      case (repoPath, branch) =>
        val repo = RepoPool.getOrCreate(repoPath, vcsFactory)
        val revisions = Helper.revisionsWithParent(repo, branch)
        val repoPathString = repoPath.toString

        sparkContext
          .parallelize(revisions)
          .flatMap({
            revision =>
              val repo = RepoPool.getOrCreate(Paths.get(repoPathString), vcsFactory)
              repo
                .computeContentDiff(revision, _.endsWith(".java"))
                .flatMap({
                  case (before, after) =>
                    val originalRepoPath = Paths.get(repoPathString)

                    val distiller =
                      CloningCommitDistillerPool
                        .getOrCreate(
                          originalRepoPath,
                          path => {
                            // this "path" may be different from the originalRepoPath
                            distillerFactory(RepoPool.getOrCreate(path, vcsFactory))
                          },
                          Thread.currentThread().getId.toString
                        )
                        .asInstanceOf[CommitDistiller[DistilledT]]

                    val tryDistill = Try(distiller.distill(before, after))
                    val metadata = repo.revisionInfo(revision)
                    val encoder = encoderFactory()
                    val values =
                      encoder
                        .toCSV(CommitDistillInfo(metadata, List(after.name -> tryDistill)))
                        .toList
                    Helper.prependProjectName(originalRepoPath, values)
                })
          })
          .saveAsTextFile(Paths.get(output.toString, Helper.projectName(repoPath)).toString)
    })
  }
}

/**
  * An execution model where repositories are processed sequentially, but the revisions of each
  * repository are processed in parallel. Each core is assigned a slice of the entire
  * revision history (of the repository currently being processed).
  * This is similar to {@link com.staslev.codedistillery.RepoCloningLocalRevisionParallel},
  * except that repositories are not cloned per core, and all cores share the same physical
  * repository on the file-system.
  */
trait RepoLocalRevisionParallelism extends ExecutionModel[SparkContext] {
  override def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {

    val sparkContext = parallelism.model

    repoBranches.foreach({
      case (repoPath, branch) =>
        val repo = vcsFactory(repoPath)
        val pathString = repoPath.toString
        val revisions = Helper.revisionsWithParent(repo, branch)
        // revisions are listed from recent to oldest
        // skip first commit on account of the big bang theory
        sparkContext
          .parallelize(Random.shuffle(revisions))
          .flatMap(revision => {

            val repo =
              RepoPool.getOrCreate(Paths.get(pathString), vcsFactory)

            val distiller =
              CommitDistillerPool
                .getOrCreate(repo, distillerFactory)
                .asInstanceOf[CommitDistiller[DistilledT]]

            val encoder = encoderFactory()
            val values = encoder.toCSV(distiller.distillCommit(revision))
            Helper.prependProjectName(repoPath, values)
          })
          .saveAsTextFile(Paths.get(output.toString, Helper.projectName(repoPath)).toString)
    })
  }
}

/**
  * An execution model where repositories and their revisions are processed in parallel.
  */
trait CrossRepoRevisionParallelism extends ExecutionModel[SparkContext] {
  override def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {

    val sparkContext = parallelism.model

    val repoRDDs = repoBranches.map({
      case (repoPath, branch) =>
        val repoPathString = repoPath.toString
        RepoRevisionsRDD
          .create(sparkContext, repoPath.toString, branch, vcsFactory)
          .map(revision => (repoPathString, revision))
    })

    sparkContext
      .union(repoRDDs.toList)
      .flatMap({
        case (repoPathString, revision) =>
          val repoPath = Paths.get(repoPathString)
          val repo = RepoPool.getOrCreate(repoPath, vcsFactory)
          val distiller =
            CommitDistillerPool
              .getOrCreate(repo, distillerFactory)
              .asInstanceOf[CommitDistiller[DistilledT]]

          repo
            .computeContentDiff(revision, _.endsWith(".java"))
            .flatMap({
              case (before, after) =>
                val tryDistill = Try(distiller.distill(before, after))
                val metadata = repo.revisionInfo(revision)
                val encoder = encoderFactory()
                val values =
                  encoder
                    .toCSV(CommitDistillInfo(metadata, List(after.name -> tryDistill)))
                    .toList
                // prepend the project name to the tuples written to output
                Helper.prependProjectName(repoPath, values)
            })
      })
      .saveAsTextFile(output.toString)
  }
}
