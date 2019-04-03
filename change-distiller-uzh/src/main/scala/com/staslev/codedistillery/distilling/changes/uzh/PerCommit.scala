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

import java.nio.file.Path

import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType
import com.staslev.codedistillery.Parallelism
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.annotation.tailrec
import scala.util.Try

object PerCommit {

  import com.staslev.codedistillery.distilling.changes.uzh.Field._

  val headerLine: String =
    (List(
      "project",
      "commitId",
      "authorName",
      "authorMail",
      "date",
      "nonTestVersatility",
      "comment",
      "testCasesAdded",
      "testCasesRemoved",
      "testCasesChanged",
      "testSuitesAdded",
      "testSuitesRemoved",
      "testSuitesAffected",
      "hasIssueRef",
      "nonTestFilesInCommit",
      "totalFilesInCommit",
      "commentLength",
      "changeTypes"
    ) ++ FineGrainedChanges.all).mkString("#")

  private val datePattern: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy")

  private val separator = "#"
  private val safeChar = '-'

  private def buildChangeRDD(inputs: Set[Path])(
      implicit sparkContext: SparkContext): RDD[Array[String]] = {

    val list =
      inputs.map(
        inputFile =>
          sparkContext
            .textFile(inputFile.toString)
            .map(line => line.split(separator)))

    sparkContext
      .union(list.toList)
      .filter(line => {
        val tryDate = Try(datePattern.parseDateTime(line(DATE)))
        if (tryDate.isFailure) {
          println(s"Could not parse date [${line(DATE)}] for project [${line(PROJECT)}]")
        }
        tryDate.isSuccess
      })
  }

  @tailrec def extractTestSuiteName(name: String): Option[String] = {
    val nameFragments = name.split("\\.")
    if (nameFragments.size > 1) {
      extractTestSuiteName(nameFragments.last)
    } else if ((name endsWith "Test") || (name endsWith "Tests") ||
               (name endsWith "TestCase") || (name startsWith "Test")) {
      Some(name)
    } else {
      None
    }
  }

  def extractTestMethodName(name: String): Option[String] = {
    def extractName(fullName: String): String = {
      val nameFragments = name.split("\\.")
      nameFragments.last.split("\\(").head
    }

    if (name.startsWith("@Test@.")) {
      Some(extractName(name))
    } else {
      val fragments = name.split("\\.").toList
      val methodName = fragments.last
      val className = fragments(fragments.length - 2)
      extractTestSuiteName(className).collect({
        case _ if methodName.startsWith("test") =>
          extractName(methodName)
      })
    }
  }

  def parentIsTestMethod(line: Array[String]): Option[String] = {
    if (line(PARENT_TYPE) == JavaEntityType.METHOD.toString) {
      extractTestMethodName(line(PARENT_UNIQUE_NAME))
    } else if (line(ROOT_TYPE) == JavaEntityType.METHOD.toString) {
      extractTestMethodName(line(ROOT_UNIQUE_NAME))
    } else {
      None
    }
  }

  def extractParentTestClass(line: Array[String]): Option[String] = {
    if (line(PARENT_TYPE) == JavaEntityType.CLASS.toString) {
      extractTestSuiteName(line(PARENT_UNIQUE_NAME))
    } else if (line(ROOT_TYPE) == JavaEntityType.CAST_EXPRESSION.toString) {
      extractTestSuiteName(line(ROOT_UNIQUE_NAME))
    } else {
      None
    }
  }

  private def clean(str: String): String = {
    str
      .replaceAll("\\r", "")
      .replaceAll(System.getProperty("line.separator"), s"$safeChar")
      .replaceAll(s"$separator", s"$safeChar")
  }

  def aggregate(inputData: Set[Path], output: Path)(
      implicit parallelism: Parallelism[SparkContext]): Unit = {

    val sparkContext = parallelism.model

    val changes = buildChangeRDD(inputData)(sparkContext)

    val perCommitChanges =
      changes
        .groupBy(line => (line(PROJECT), line(COMMIT_ID)))
        .mapValues(changeLines => {
          val testCasesAdded =
            changeLines
              .collect({
                case line if line(CHANGE_TYPE) == "ADDITIONAL_FUNCTIONALITY" =>
                  extractTestMethodName(line(UNIQUE_NAME)).map((line, _))
              })
              .flatten
              .toSet

          val testCasesRemoved = changeLines
            .collect({
              case line if line(CHANGE_TYPE) == "REMOVED_FUNCTIONALITY" =>
                extractTestMethodName(line(UNIQUE_NAME)).map((line, _))
            })
            .flatten
            .toSet

          val testCasesChanged = changeLines
            .collect({
              case line =>
                parentIsTestMethod(line).map((line, _))
            })
            .flatten
            .toSet

          val testSuitesAdded = changeLines
            .collect({
              case line if line(CHANGE_TYPE) == "ADDITIONAL_CLASS" =>
                extractTestSuiteName(line(UNIQUE_NAME)).map((line, _))
            })
            .flatten
            .toSet

          val testSuitesRemoved = changeLines
            .collect({
              case line if line(CHANGE_TYPE) == "REMOVED_CLASS" =>
                extractTestSuiteName(line(UNIQUE_NAME)).map((line, _))
            })
            .flatten
            .toSet

          val testSuitesAffected = changeLines
            .collect({
              case line =>
                extractParentTestClass(line).map((line, _))
            })
            .flatten
            .toSet

          val testRelatedLines =
            List(testCasesAdded,
                 testCasesChanged,
                 testCasesRemoved,
                 testSuitesAdded,
                 testSuitesRemoved,
                 testSuitesAffected)
              .flatMap(_.map(_._1))

          // needs fixing
          val changesWithoutTests =
            (changeLines.toSet -- testRelatedLines.toSet)
              .filter(line => {
                val filenameWithoutExt = line(FILENAME)
                  .split("/")
                  .last
                  .split("\\.")
                  .head
                extractTestSuiteName(filenameWithoutExt).isEmpty
              })

          val nonTestVersatility = changesWithoutTests.map(_(CHANGE_TYPE)).size

          val changeTypeHistogram: Map[String, Int] = changesWithoutTests
            .map(entry => entry(CHANGE_TYPE))
            .groupBy(identity)
            .mapValues(_.size)

          val comment = changeLines.head.apply(COMMENT)
          val authorMail = changeLines.head.apply(EMAIL)
          val authorName = changeLines.head.apply(NAME)
          val commitDate =
            datePattern
              .parseDateTime(changeLines.head.apply(DATE))
              .toInstant
              .getMillis / 1000

          val hasIssueRef =
            """\b[A-Z]+\-\d+\b""".r.pattern.matcher(comment).find()

          val commentLength = comment.split(" ").length

          val nonTestFilesInCommit = {
            def shortFilename(line: Array[String]) = {
              line(FILENAME).split("/").last.split("\\.").head
            }

            changeLines
              .collect({
                case changeLine if extractTestSuiteName(shortFilename(changeLine)).isEmpty =>
                  changeLine(FILENAME)
              })
              .toSet
              .size
          }

          val totalFilesInCommit = changeLines.map(_(FILENAME)).toSet.size

          List(
            authorName,
            authorMail,
            commitDate,
            nonTestVersatility,
            comment,
            testCasesAdded.map(_._2).size,
            testCasesRemoved.map(_._2).size,
            testCasesChanged.map(_._2).size,
            testSuitesAdded.map(_._2).size,
            testSuitesRemoved.map(_._2).size,
            testSuitesAffected.map(_._2).size,
            hasIssueRef,
            nonTestFilesInCommit,
            totalFilesInCommit,
            commentLength,
            changeTypeHistogram.keySet.toList.sorted.mkString(",")
          ) ++
            FineGrainedChanges.all.map(changeTypeHistogram.getOrElse(_, 0))
        })

    perCommitChanges
      .map({
        case ((project, commitId), values) =>
          (project :: commitId :: values.map(_.toString))
            .map(clean)
            .mkString("#")
      })
      .saveAsTextFile(output.toString)
  }

}
