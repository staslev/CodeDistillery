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

import java.io.{File, FileFilter}
import java.nio.file.{Path, Paths}

import org.apache.commons.io.{FileUtils, FilenameUtils, IOCase}

object CloningCommitDistillerPool extends ObjectPool[Path, CommitDistiller[_]] {

  private var instances: Map[Path, CommitDistiller[_]] = Map()

  def closeAll(): Unit = {
    // no need to close the git repos since we delete it anyway
    instances.keys.map(_.toFile).foreach(FileUtils.deleteDirectory)
  }

  def clearTempFoldersOf(project: Path): Unit = {

    def ofProject(path: Path): FileFilter = new FileFilter {

      private val shortProjectTemplate = {
        val projectTemplate = buildTempDirPath(path, "*").toString
        projectTemplate.substring(FilenameUtils.indexOfLastSeparator(projectTemplate) + 1)
      }

      override def accept(file: File): Boolean = {
        FilenameUtils.wildcardMatch(file.getName, shortProjectTemplate, IOCase.INSENSITIVE)
      }
    }

    val oldDirs = new File(System.getProperty("java.io.tmpdir")).listFiles(ofProject(project))

    oldDirs.foreach(FileUtils.deleteDirectory)
  }

  private def buildTempDirPath(projectPath: Path, executorId: String): Path = {
    val shortTempDirName = {
      val fullPath = projectPath.toString
      val userDotRepo = fullPath.substring(FilenameUtils.indexOfLastSeparator(fullPath) + 1)
      userDotRepo + "." + executorId
    }
    Paths.get(System.getProperty("java.io.tmpdir"), shortTempDirName)
  }

  override def getOrCreate(key: Path,
                           factory: Path => CommitDistiller[_],
                           executorId: String): CommitDistiller[_] = {
    CommitDistillerPool.synchronized {
      val localRepoPath = buildTempDirPath(key, executorId)
      localRepoPath.toFile.deleteOnExit()
      if (!instances.contains(localRepoPath)) {
        FileUtils.copyDirectory(key.toFile, localRepoPath.toFile)
        instances = instances.updated(localRepoPath, factory(localRepoPath))
        println(s"Created new CommitDistiller for $executorId")
      }
      instances(localRepoPath)
    }
  }
}
