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

import java.io.{File, FileWriter, PrintWriter}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import org.apache.commons.io.FilenameUtils

class DistillOutputWriter[DistilledT](encoder: DistillOutputCSVEncoder[DistilledT],
                                      outputFile: Path) {

  private val tempFilePath =
    Paths.get(
      FilenameUtils.getFullPath(outputFile.toString),
      FilenameUtils.getBaseName(outputFile.toString) + "." +
        FilenameUtils.getExtension(outputFile.toString) + ".tmp"
    )

  new File(tempFilePath.toString).delete()

  protected val writer: PrintWriter = new PrintWriter(new FileWriter(tempFilePath.toString))

  def write(distilledResult: CommitDistillInfo[DistilledT]): Unit = {
    encoder.toCSV(distilledResult).foreach(writer.println)
  }

  def flush(): Unit = {
    writer.flush()
  }

  def close(): Unit = {
    writer.close()
    Files.move(tempFilePath, outputFile, StandardCopyOption.REPLACE_EXISTING)
  }
}
