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

package com.staslev.codedistillery.vcs

import java.io._
import java.nio.file.Path
import java.text.SimpleDateFormat

import com.staslev.codedistillery.{CommitMetadata, Content, NamedContent}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.diff.DiffEntry.ChangeType
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}

object GitRepo {
  def apply(path: Path): SourceControlledRepo = {
    new GitRepo(path)
  }
}

class GitRepo private (override val path: Path) extends SourceControlledRepo {

  import org.eclipse.jgit.storage.file.FileRepositoryBuilder

  import scala.collection.JavaConverters._

  private val repo = new FileRepositoryBuilder()
    .setWorkTree(path.toFile)
    .setMustExist(true)
    .build()

  private val git = new Git(repo)

  sys.ShutdownHookThread {
    git.close()
    repo.close()
  }

  @throws[IOException]
  private def getCanonicalTreeParser(commitId: ObjectId) = {
    import org.eclipse.jgit.treewalk.CanonicalTreeParser
    val walk = new RevWalk(git.getRepository)
    try {
      val commit = walk.parseCommit(commitId)
      val treeId = commit.getTree.getId
      val reader = git.getRepository.newObjectReader
      try {
        new CanonicalTreeParser(null, reader, treeId)
      } finally {
        if (reader != null) {
          reader.close()
        }
      }

    } finally {
      if (walk != null) {
        walk.close()
      }
    }
  }

  private def gitContentOf(objectId: ObjectId): String = {
    if (objectId == ObjectId.zeroId()) {
      ""
    } else {
      val loader = repo.open(objectId)
      val rawText = new RawText(loader.getBytes)
      rawText.getString(0, rawText.size(), false)
    }
  }

  private def walkRevisionRange[T](
      fromCommit: RevCommit,
      toCommit: RevCommit,
      revWalk: RevWalk = new RevWalk(repo))(f: RevCommit => T): List[T] = {
    try {
      revWalk.markStart(fromCommit)
      revWalk.markUninteresting(toCommit)
      revWalk.iterator().asScala.map(f).toList
    } finally {
      if (revWalk != null) {
        revWalk.dispose()
      }
    }
  }

  private def diffsIn(revision: String, filenameFilter: String => Boolean = _ => true)
    : Iterable[((String, AbbreviatedObjectId), (String, AbbreviatedObjectId))] = {

    val newCommit = repo.resolve(revision)
    val oldCommit = repo.resolve(s"$revision^1")

    if (oldCommit == null) {
      List()
    } else {
      val diffEntries =
        git
          .diff()
          .setOldTree(getCanonicalTreeParser(oldCommit))
          .setNewTree(getCanonicalTreeParser(newCommit))
          .call()

      import org.eclipse.jgit.diff.RenameDetector
      val rd = new RenameDetector(repo)
      rd.addAll(diffEntries)
      val processedDiffs = rd.compute().asScala

      processedDiffs
        .filterNot(_.getChangeType == ChangeType.RENAME)
        .map(diff => ((diff.getOldPath, diff.getOldId), (diff.getNewPath, diff.getNewId)))
        .filter({
          case ((oldName, _), (newName, _)) =>
            filenameFilter(oldName) || filenameFilter(newName)
        })
    }
  }

  override def computeContentDiff(
      revision: String,
      filenameFilter: String => Boolean = _ => true): Iterable[(NamedContent, NamedContent)] = {
    diffsIn(revision, filenameFilter)
      .map({
        case ((oldName, oldObjId), (newName, newObjId)) =>
          (Content(gitContentOf(oldObjId.toObjectId)).withName(oldName),
           Content(gitContentOf(newObjId.toObjectId)).withName(newName))
      })
  }

  override def revisions(branch: String): Iterable[String] = {
    val commits = git.log().add(repo.resolve(s"remotes/origin/$branch")).call()
    commits.asScala.map(_.getId.getName)
  }

  override def revisionInfo(revision: String): CommitMetadata = {
    import org.eclipse.jgit.lib.ObjectId
    val walk = new RevWalk(repo)
    try {
      val commit = walk.parseCommit(ObjectId.fromString(revision))
      CommitMetadata(
        revision,
        commit.getAuthorIdent.getName,
        commit.getAuthorIdent.getEmailAddress,
        // customizing the date pattern since the original format is time zone ambiguous
        new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy").format(commit.getAuthorIdent.getWhen),
        commit.getFullMessage
      )
    } finally {
      if (walk != null) {
        walk.close()
      }
    }
  }

  private def filesChangedIn(revision: String): Iterable[(String, String)] = {
    val newCommit = repo.resolve(revision)
    val oldCommit = repo.resolve(s"$revision^1")
    val diffEntries =
      git
        .diff()
        .setOldTree(getCanonicalTreeParser(oldCommit))
        .setNewTree(getCanonicalTreeParser(newCommit))
        .call()

    diffEntries.asScala.map(diff => (diff.getOldPath, diff.getNewPath))
  }

  override def revisions(branch: String, author: String): Iterable[String] = {
    import org.eclipse.jgit.revwalk.filter.AuthorRevFilter
    val authorFilter = AuthorRevFilter.create(author)
    git.log().setRevFilter(authorFilter).call().asScala.map(_.getName)
  }

  override def close(): Unit = {
    git.close()
    repo.close()
  }

  override def hasChanged(filenames: String*)(from: String, to: String): Boolean = {
    def hasChangesIn(filenames: String*)(revCommit: RevCommit) = {
      val postChangeFilenames = filesChangedIn(revCommit.getName).map(_._2)
      filenames.exists(filename => postChangeFilenames.exists(_.endsWith(filename)))
    }

    val revWalk = new RevWalk(repo)
    val toCommit = revWalk.parseCommit(repo.resolve(to))
    val fromCommit = revWalk.parseCommit(repo.resolve(from))

    hasChangesIn(filenames: _*)(toCommit) ||
    walkRevisionRange(fromCommit, toCommit, revWalk)(hasChangesIn(filenames: _*)).contains(true)
  }

  override def checkout(revision: String, file: Path): Unit = {
    file.toString match {
      case "*" =>
        git.reset().setMode(ResetType.HARD).call()
        git.checkout().setAllPaths(true).setStartPoint(revision).call()
      case _ =>
        // discard all change to 'file'
        //git.checkout.addPath(file.toString).call()
        git.reset().setMode(ResetType.HARD).call()
        // checkout 'file' from the specified revision
        git.checkout.addPath(file.toString).setStartPoint(revision).call()
    }
  }
}
