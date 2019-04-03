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
import java.nio.file.Path

import com.staslev.codedistillery.vcs.SourceControlledRepo

trait Parallelism[T] {
  def model: T
}

trait ExecutionModel[ModelT] {

  type DistilledT

  def vcsFactory: Path => SourceControlledRepo
  def distillerFactory: SourceControlledRepo => CommitDistiller[DistilledT]
  def encoderFactory: () => DistillInfoCSVEncoder[DistilledT]

  /**
    * Distill a number of projects in parallel using Spark as the parallelism engined.
    * The distilled changes will be written to a file which is a concatenation of the specified
    * `output` parameter and the projects names along with a csv extension.
    * This will result in a file per project.
    *
    * @param repoBranches The repositories paired with a specific branch to distill changes from
    * @param output The name of the file where results will be written to (per project)
    * @param parallelism The parallelism model to be used
    */
  def distill(repoBranches: Set[(Path, String)], output: Path)(
      implicit parallelism: Parallelism[ModelT]): Unit
}
