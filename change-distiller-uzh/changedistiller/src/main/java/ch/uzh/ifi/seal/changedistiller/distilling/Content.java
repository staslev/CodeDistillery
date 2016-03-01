package ch.uzh.ifi.seal.changedistiller.distilling;

/*
 * #%L
 * ChangeDistiller
 * %%
 * Modifications Copyright (C) 2019 Stas Levin
 * Copyright (C) 2011 - 2015 Software Architecture and Evolution Lab, Department of Informatics, UZH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ch.uzh.ifi.seal.changedistiller.ast.FileUtils;

import java.io.File;

/** Created by slevin on 9/15/15. */
public class Content {

  public static final String DEFAULT = "default";
  private String content;
  private String name;
  private String contentLangVersion;

  public Content(final String content, final String name) {
    this(content, name, "default");
  }

  public Content(final String content, final String name, final String contentLangVersion) {
    this.content = content;
    this.name = name;
    this.contentLangVersion = contentLangVersion;
  }

  public static Content fromFile(File file) {
    return fromFile(file, DEFAULT);
  }

  public static Content fromFile(File file, String contentLangVersion) {
    return new Content(FileUtils.getContent(file), file.getName(), contentLangVersion);
  }

  public String getContent() {
    return content;
  }

  public String getName() {
    return name;
  }

  public String getContentLangVersion() {
    return contentLangVersion;
  }
}
