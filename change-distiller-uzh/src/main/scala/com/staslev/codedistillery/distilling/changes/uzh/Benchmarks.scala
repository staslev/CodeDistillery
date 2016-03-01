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

package com.staslev.codedistillery.distilling.changes.uzh
import java.io.File
import java.nio.file.{Path, Paths}

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange
import com.staslev.codedistillery._
import com.staslev.codedistillery.vcs.GitRepo
import org.apache.spark.SparkContext

object Benchmarks {

  private val vcsFactory = GitRepo.apply _
  private val distillerFactory = UzhSourceCodeChangeDistiller.apply _
  private val encoder = UzhSourceCodeChangeCSVEncoder

  private def distillSequential(repoPath: Path, branch: String, output: Path): Double = {
    val start = System.currentTimeMillis()
    val repo = GitRepo(repoPath)
    val distiller = distillerFactory.apply(repo)
    val writer = new DistillOutputWriter(encoder, output)

    // revisions are listed from recent to oldest
    // skip first commit on account of the big bang theory
    repo
      .revisions(branch)
      .toList
      .reverse
      .drop(1)
      .foreach(revision => writer.write(distiller.distillCommit(revision)))

    writer.close()

    (System.currentTimeMillis() - start) / 1000.toDouble
  }

  private def distillRevisionParallel(repoPath: Path, branch: String, output: Path)(
      implicit sparkContext: SparkContext): Double = {
    val start = System.currentTimeMillis()

    val repo = vcsFactory(repoPath)
    val pathString = repoPath.toString

    val revisions = repo.revisions(branch).toList.reverse.drop(1)

    // revisions are listed from recent to oldest
    // skip first commit on account of the big bang theory
    sparkContext
      .parallelize(revisions)
      .flatMap(revision => {

        val repo =
          RepoPool.getOrCreate(Paths.get(pathString), vcsFactory)

        val distiller =
          CommitDistillerPool
            .getOrCreate(repo, distillerFactory)
            .asInstanceOf[CommitDistiller[SourceCodeChange]]

        encoder.toCSV(distiller.distillCommit(revision))
      })
      .saveAsTextFile(output.toString)

    (System.currentTimeMillis() - start) / 1000.toDouble
  }

  private def distillMultiRepo(repoPath: Path, branch: String, output: Path)(
      implicit sparkContext: SparkContext): Double = {
    CodeDistillery(vcsFactory, distillerFactory, encoder)
      .distill(Set((repoPath, branch)), output)
  }

  private def deleteDirectory(directoryToBeDeleted: File): Boolean = {
    Option(directoryToBeDeleted.listFiles).foreach(_.foreach(deleteDirectory))
    directoryToBeDeleted.delete
  }

  def runOn(repoPath: Path, branch: String): Seq[(String, Double)] = {

    import CodeDistillery.localSparkCluster

    val distillModes =
      List(("sequential", distillSequential _),
           ("singleRepoRevisionParallel", distillRevisionParallel _),
           ("multiRepoRevisionParallel", distillMultiRepo _))

    distillModes.map({
      case (descriptor, distillMethod) => {
        val benchmarkDir = Paths.get(repoPath.toString, "distill-benchmarks")
        benchmarkDir.toFile.mkdir()
        val distillOutput = Paths.get(benchmarkDir.toString, descriptor)
        val elapsed = distillMethod(repoPath, branch, distillOutput)
        deleteDirectory(benchmarkDir.toFile)
        (descriptor, elapsed)
      }
    })
  }
}
