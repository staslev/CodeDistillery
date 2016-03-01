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

object NaiveCommentClassifier {

  abstract class Category(val alias: String)
  case object Corrective extends Category("c")
  case object Perfective extends Category("p")
  case object Adaptive extends Category("a")
  case object Unknown extends Category("x")

  private val identity = (s: String) => s"$s"
  private val startsWithWordBoundary = (s: String) => s"\\b$s"
  private val endsWithWordBoundary = (s: String) => s"$s\\b"

  private val correctiveCommentMatcher =
    (".*(" +
      Set("fix", "resolv", "clos", "handl", "issue", "defect", "bug", "problem", "ticket")
        .map(startsWithWordBoundary)
        .mkString("|") +
      ").*").r

  private val perfectiveCommentMatcher =
    (".*(" +
      Set("refactor",
          "re-factor",
          "reimplement",
          "re-implement",
          "design",
          "re-design",
          "replac",
          "modify",
          "updat",
          "upgrad",
          "cleanup",
          "clean-up")
        .map(startsWithWordBoundary)
        .mkString("|") +
      ").*").r

  private val adaptiveCommentMatcher =
    (".*(" +
      Set("add", "new", "introduc", "implement", "implemented", "extend", "feature", "support")
        .map(startsWithWordBoundary)
        .mkString("|") +
      ").*").r

  def isCorrectiveComment(comment: String): Boolean = {
    comment.toLowerCase match {
      case correctiveCommentMatcher(term) => true
      case _                              => false
    }
  }

  def isPerfectiveComment(comment: String): Boolean = {
    val lowerComment = comment.toLowerCase
    lowerComment match {
      case perfectiveCommentMatcher(term)
          if correctiveCommentMatcher.findAllIn(lowerComment).isEmpty &&
            adaptiveCommentMatcher.findAllIn(lowerComment).isEmpty =>
        true
      case _ => false
    }
  }

  def isAdaptiveComment(comment: String): Boolean = {
    val lowerComment = comment.toLowerCase
    lowerComment match {
      case adaptiveCommentMatcher(term)
          if correctiveCommentMatcher.findAllIn(lowerComment).isEmpty =>
        true
      case _ => false
    }

  }

  def classify(comment: String): Category = {
    if (NaiveCommentClassifier.isCorrectiveComment(comment)) {
      Corrective
    } else if (NaiveCommentClassifier.isPerfectiveComment(comment)) {
      Perfective
    } else if (NaiveCommentClassifier.isAdaptiveComment(comment)) {
      Adaptive
    } else {
      Unknown
    }
  }
}
