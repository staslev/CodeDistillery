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

import com.staslev.codedistillery.vcs.SourceControlledRepo

import scala.util.Try

abstract class CommitDistiller[DistilledT](repo: SourceControlledRepo,
                                           filenameFilter: String => Boolean = _ => true)
    extends ContentDiffDistiller[DistilledT] {

  def prepareDistill(commitId: String): Unit = {}

  def isWorkingDirectoryAware: Boolean = false

  def distillCommit(commitId: String): CommitDistillInfo[DistilledT] = {

    val diffs = repo.computeContentDiff(commitId, filenameFilter)

    val commitDistillingResult =
      if (diffs.nonEmpty) {
        prepareDistill(commitId)
        diffs.map({
          case (beforeContent, afterContent) =>
            afterContent.name -> Try(distill(beforeContent, afterContent))
        })
      } else {
        List()
      }

    CommitDistillInfo(repo.revisionInfo(commitId), commitDistillingResult)
  }
}
