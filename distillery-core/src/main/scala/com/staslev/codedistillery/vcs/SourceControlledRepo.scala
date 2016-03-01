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

import java.nio.file.{Path, Paths}

import com.staslev.codedistillery.{CommitMetadata, NamedContent}

object SourceControlledRepo {

  def none(): SourceControlledRepo = new SourceControlledRepo {

    override def revisionInfo(revision: String): CommitMetadata = ???

    override def revisions(branch: String): Iterable[String] = List()

    override def computeContentDiff(
        revision: String,
        filenameFilter: String => Boolean = _ => true): Iterable[(NamedContent, NamedContent)] =
      List()

    override def revisions(branch: String, author: String): Iterable[String] = List()

    override def close(): Unit = {}

    override def path: Path = Paths.get("")

    override def hasChanged(filenames: String*)(from: String, to: String): Boolean = false

    override def checkout(revision: String, file: Path): Unit = {}

  }
}

trait SourceControlledRepo {

  def path: Path

  def revisionInfo(revision: String): CommitMetadata

  def computeContentDiff(
      revision: String,
      filenameFilter: String => Boolean = _ => true): Iterable[(NamedContent, NamedContent)]

  def revisions(branch: String): Iterable[String]

  def revisions(branch: String, author: String): Iterable[String]

  def hasChanged(filenames: String*)(from: String, to: String): Boolean

  def checkout(revision: String, file: Path = Paths.get("*")): Unit

  def close(): Unit
}
