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

import com.staslev.codedistillery.CommitDistillInfo.{CommitDistillResult, ContentDistillResult}

import scala.util.{Failure, Success, Try}

object CommitDistillInfo {
  type ContentDistillResult[T] = Try[Iterable[Try[T]]]
  type CommitDistillResult[T] = Iterable[(String, ContentDistillResult[T])]
}

case class CommitMetadata(commitId: String,
                          authorName: String,
                          authorEmail: String,
                          date: String,
                          msg: String)

case class CommitDistillInfo[DistilledT](commitInfo: CommitMetadata,
                                         distillInfo: CommitDistillResult[DistilledT]) {

  private def successOnly(contentDistillResult: ContentDistillResult[DistilledT]) = {
    contentDistillResult match {
      case Success(maybeChanges) =>
        maybeChanges.flatMap({
          case Success(change) => Some(change)
          case _               => None
        })
      case _ => List()
    }
  }

  private def errorsOnly(
      contentDistillResult: ContentDistillResult[DistilledT]): Iterable[Throwable] = {
    contentDistillResult match {
      case Failure(e) => List(e)
      case Success(maybeChanges) =>
        maybeChanges.flatMap({
          case Failure(e) => Some(e)
          case _          => None
        })
      case _ => List()
    }
  }

  def successOnly(): Iterable[(String, Iterable[DistilledT])] = {
    distillInfo.flatMap({
      case (file, fileDistillResult) =>
        Some(file -> successOnly(fileDistillResult))
    })
  }

  def errorsOnly(): Iterable[Throwable] = {
    distillInfo.flatMap({
      case (_, fileDistillResult) =>
        errorsOnly(fileDistillResult)
    })
  }
}
