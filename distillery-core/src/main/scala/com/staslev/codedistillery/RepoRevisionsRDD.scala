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

import java.io.{IOException, ObjectOutputStream, OutputStream}
import java.nio.file.{Path, Paths}

import com.staslev.codedistillery.vcs.SourceControlledRepo
import org.apache.spark.rdd.RDD
import org.apache.spark.serializer.{JavaSerializer, SerializationStream, SerializerInstance}
import org.apache.spark.{Partition, SparkContext, SparkEnv, TaskContext}

import scala.util.Random

object RepoRevisionsRDD {

  private def partition(revisions: List[String], numOfPartitions: Int): Iterator[Array[String]] = {
    if (numOfPartitions < 1) {
      throw new IllegalArgumentException("Positive number of partitions required")
    }

    def slicesOf(length: Long, numSlices: Int): Iterator[(Int, Int)] = {
      (0 until numSlices).iterator.map { i =>
        val start = ((i * length) / numSlices).toInt
        val end = (((i + 1) * length) / numSlices).toInt
        (start, end)
      }
    }
    val array = revisions.toArray
    slicesOf(array.length, numOfPartitions).map({
      case (start, end) =>
        array.slice(start, end)
    })
  }

  /**
    * Create an RDD of string, where each strings is a revision in the specified branch and
    * repository.
    *
    * @param repo a file system based string path to the repository directory.
    * @param branch a branch within the specified repository to take revisions from.
    * @param keepNaturalRevisionOrder whether to maintain revision order within partitions.
    *                                 If `true`, the RDD partitions will be ranges of successive
    *                                 revisions, otherwise, no guarantee is given as to the order
    *                                 or revisions with partitions.
    * @return an {@code RDD[String]},
    *      where each string is a revision from the specified branch and repository.
    */
  def create(sc: SparkContext,
             repo: String,
             branch: String,
             vcsFactory: Path => SourceControlledRepo,
             keepNaturalRevisionOrder: Boolean = false,
             locationPrefs: Map[Int, Seq[String]] = Map()): RDD[String] = {
    new RepoRevisionsRDD(sc,
                         repo,
                         branch,
                         vcsFactory,
                         sc.defaultParallelism,
                         keepNaturalRevisionOrder,
                         locationPrefs)
  }
}

private class RepoRevisionsRDD(sc: SparkContext,
                               repo: String,
                               branch: String,
                               vcsFactory: Path => SourceControlledRepo,
                               numOfPartitions: Int,
                               keepNaturalRevisionOrder: Boolean,
                               locationPrefs: Map[Int, Seq[String]])
    extends RDD[String](sc, Nil) {

  override def compute(split: Partition, context: TaskContext): Iterator[String] = {
    split
      .asInstanceOf[RevisionsPartition]
      .revisions
      .toIterator
  }

  override protected def getPartitions: Array[Partition] = {
    val vcs = RepoPool.getOrCreate(Paths.get(repo), vcsFactory)
    // drop initial commit
    val repoRevisions = vcs.revisions(branch).toList.dropRight(1)
    val revisions =
      if (keepNaturalRevisionOrder) {
        repoRevisions
      } else {
        // shuffling helps considerably in making the workload distribute evenly across partitions
        // unless shuffled, empirical testing indicates some partitions tend to become "hot",
        // probably due patterns that emerge when processing ranges of successive commits
        Random.shuffle(repoRevisions)
      }
    val partitions = RepoRevisionsRDD.partition(revisions, numOfPartitions).toArray
    partitions.indices.map(i => new RevisionsPartition(id, i, partitions(i))).toArray
  }

  override def getPreferredLocations(p: Partition): Seq[String] = {
    locationPrefs.getOrElse(p.index, Nil)
  }
}

private class RevisionsPartition(val rddId: Long, val slice: Int, val revisions: Seq[String])
    extends Partition
    with Serializable {

  override def hashCode(): Int = (71 * (71 + rddId) + slice).toInt

  override def equals(other: Any): Boolean = other match {
    case that: RevisionsPartition =>
      this.rddId == that.rddId && this.slice == that.slice
    case _ => false
  }

  override def index: Int = slice

  def serializeViaNestedStream(os: OutputStream, ser: SerializerInstance)(
      f: SerializationStream => Unit): Unit = {

    val osWrapper = ser.serializeStream(new OutputStream {
      override def write(b: Int): Unit = os.write(b)
      override def write(b: Array[Byte], off: Int, len: Int): Unit = os.write(b, off, len)
    })

    try {
      f(osWrapper)
    } finally {
      osWrapper.close()
    }
  }

  //noinspection ScalaUnusedSymbol
  @throws(classOf[IOException])
  private def writeObject(out: ObjectOutputStream): Unit = {

    val serializer = SparkEnv.get.serializer

    // Treat java serializer with default action rather than going through serialization,
    // to avoid a separate serialization header.

    serializer match {
      case js: JavaSerializer => out.defaultWriteObject()
      case _ =>
        out.writeLong(rddId)
        out.writeInt(slice)

        val ser = serializer.newInstance()
        serializeViaNestedStream(out, ser)(_.writeObject(revisions))
    }
  }
}
