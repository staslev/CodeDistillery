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

import com.staslev.codedistillery._
import com.staslev.codedistillery.vcs.GitRepo
import org.apache.spark.SparkContext

import scala.reflect.ClassTag

object Benchmarks {

  private val vcsFactory = GitRepo.apply _
  private val distillerFactory = UzhSourceCodeChangeDistiller.apply _
  private val encoderFactory = () => UzhSourceCodeChangeCSVEncoder

  private implicit val model: Parallelism[SparkContext] = LocalSparkParallelism.spark

  private def time(block: => Unit): Double = {
    val start = System.currentTimeMillis()
    block
    (System.currentTimeMillis() - start) / 1000.toDouble
  }

  private def deleteDirectory(directoryToBeDeleted: File): Boolean = {
    Option(directoryToBeDeleted.listFiles).foreach(_.foreach(deleteDirectory))
    directoryToBeDeleted.delete
  }

  def runOn(repoPath: Path, branch: String): Seq[(String, Double)] = {

    def nameOf[T: ClassTag]: String = implicitly[ClassTag[T]].runtimeClass.getSimpleName

    val benchmarks =
      List(
        nameOf[SequentialExecution] ->
          new CodeDistillery(vcsFactory, distillerFactory, encoderFactory) with SequentialExecution,
        nameOf[RepoLocalRevisionParallelism] ->
          new CodeDistillery(vcsFactory, distillerFactory, encoderFactory)
          with RepoLocalRevisionParallelism,
        nameOf[CrossRepoRevisionParallelism] ->
          new CodeDistillery(vcsFactory, distillerFactory, encoderFactory)
          with CrossRepoRevisionParallelism
      )

    benchmarks.map({
      case (descriptor, distillery) =>
        val benchmarkDir = Paths.get(repoPath.toString, "distill-benchmarks")
        benchmarkDir.toFile.mkdir()
        val distillOutput = Paths.get(benchmarkDir.toString, descriptor)
        val elapsed = time(distillery.distill(Set((repoPath, branch)), distillOutput))
        deleteDirectory(benchmarkDir.toFile)
        (descriptor, elapsed)
    })
  }
}
