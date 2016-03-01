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

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange
import com.staslev.codedistillery.{CommitDistillInfo, DistillOutputCSVEncoder}

object UzhSourceCodeChangeCSVEncoder extends DistillOutputCSVEncoder[SourceCodeChange] {
  import com.staslev.codedistillery.CSVUtils._
  def toCSV(distilled: CommitDistillInfo[SourceCodeChange]): Iterable[String] = {
    distilled
      .successOnly()
      .flatMap({
        case (filename, changes) =>
          changes.map(change => {
            s"${clean(distilled.commitInfo.commitId)}" +
              s"$separator${clean(distilled.commitInfo.date)}" +
              s"$separator${clean(distilled.commitInfo.authorName)}" +
              s"$separator${clean(distilled.commitInfo.authorEmail)}" +
              s"$separator${clean(change.getChangeType.toString)}" +
              s"$separator${clean(change.getChangedEntity.getUniqueName)}" +
              s"$separator${clean(change.getSignificanceLevel.toString)}" +
              s"$separator${clean(change.getParentEntity.getType.toString)}" +
              s"$separator${clean(change.getParentEntity.getUniqueName)}" +
              s"$separator${clean(change.getRootEntity.getType.toString)}" +
              s"$separator${clean(change.getRootEntity.getUniqueName)}" +
              s"$separator${clean(distilled.commitInfo.msg)}" +
              s"$separator${clean(filename)}"
          })
      })
  }
}
