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

import scala.util.Try

object CodeDistillery {

  implicit val localSparkCluster: SparkContext =
    new SparkContext(
      new SparkConf()
        .setMaster(s"local[*]")
        .setAppName("SemanticChangeAggregation")
        .set("spark.driver.memory", "10g")
        .set("spark.executor.memory", "2g")
        .set("spark.executor.heartbeatInterval", "20s"))

  def apply[T](vcs: Path => SourceControlledRepo,
               distillerFactory: SourceControlledRepo => CommitDistiller[T],
               encoder: DistillOutputCSVEncoder[T]): CodeDistillery[T] = {
    new CodeDistillery[T](vcs, distillerFactory, encoder)
  }
}

class CodeDistillery[T] private (vcsFactory: Path => SourceControlledRepo,
                                 distillerFactory: SourceControlledRepo => CommitDistiller[T],
                                 encoder: DistillOutputCSVEncoder[T])
    extends Serializable {

  private def projectName(repoPath: Path): String = {
    repoPath.getName(repoPath.getNameCount - 1).toString
  }

  /**
    * Distill a number of projects in parallel using Spark as the parallelism engined.
    * The distilled changes will be written to a file which is a concatenation of the specified
    * `output` parameter and the projects names along with a csv extension.
    * This will result in a file per project.
    *
    * @param repoBranches The repositories paired with a specific branch to distill changes from
    * @param output The name of the file where results will be written to (per project)
    * @param sparkContext The spark context to be used
    */
  def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit sparkContext: SparkContext): Double = {
    val start = System.currentTimeMillis()

    sparkContext
      .parallelize(repoBranches.toList.map({ case (path, branch) => (path.toString, branch) }))
      .flatMap({
        case (repoPathString, branch) =>
          val repoPath = Paths.get(repoPathString)
          val repo = RepoPool.getOrCreate(repoPath, vcsFactory)
          // ignore the initial commit revision
          val revisions = repo.revisions(branch).toList.reverse.drop(1)
          revisions.map((repoPathString, _))
      })
      .repartition(sparkContext.defaultParallelism)
      .flatMap({
        case (repoPathString, revision) =>
          val repoPath = Paths.get(repoPathString)
          val repo = RepoPool.getOrCreate(repoPath, vcsFactory)
          repo
            .computeContentDiff(revision, _.endsWith(".java"))
            .map((repoPathString, revision, _))
      })
      .flatMap({
        case (repoPathString, revision, (before, after)) =>
          val repoPath = Paths.get(repoPathString)
          val repo = RepoPool.getOrCreate(repoPath, vcsFactory)
          val distiller =
            CommitDistillerPool
              .getOrCreate(repo, distillerFactory)
              .asInstanceOf[CommitDistiller[T]]
          val tryDistill = Try(distiller.distill(before, after))
          val metadata = repo.revisionInfo(revision)
          val values =
            encoder
              .toCSV(CommitDistillInfo(metadata, List(after.name -> tryDistill)))
              .toList
          // prepend the project name to the tuples written to output

          values.map(str => projectName(repoPath) + CSVUtils.separator + str)
      })
      .saveAsTextFile(output.toString)

    (System.currentTimeMillis() - start) / 1000.toDouble
  }
}
