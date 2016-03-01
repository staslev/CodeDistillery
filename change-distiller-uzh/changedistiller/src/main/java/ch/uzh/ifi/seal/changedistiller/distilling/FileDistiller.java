package ch.uzh.ifi.seal.changedistiller.distilling;

/*
 * #%L
 * ChangeDistiller
 * %%
 * Modifications Copyright (C) 2019 Stas Levin
 * Copyright (C) 2011 - 2013 Software Architecture and Evolution Lab, Department of Informatics, UZH
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

import ch.uzh.ifi.seal.changedistiller.ast.ASTHelperFactory;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidateProcessor;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import com.google.inject.Inject;

import java.io.File;

/**
 * Distills {@link SourceCodeChange}s between two {@link File}.
 *
 * @author Beat Fluri
 * @author Giacomo Ghezzi
 */
public class FileDistiller extends ContentDistiller {

  @Inject
  FileDistiller(
      DistillerFactory distillerFactory,
      ASTHelperFactory factory,
      RefactoringCandidateProcessor refactoringProcessor) {
    super(distillerFactory, factory, refactoringProcessor);
  }

  /**
   * Extracts classified {@link SourceCodeChange}s between two {@link File}s.
   *
   * @param left file to extract changes
   * @param right file to extract changes
   */
  public void extractClassifiedSourceCodeChanges(File left, File right) {
    extractClassifiedSourceCodeChanges(Content.fromFile(left), Content.fromFile(right));
  }

  /**
   * Extracts classified {@link SourceCodeChange}s between two {@link File}s.
   *
   * @param left file to extract changes
   * @param leftVersion version of the language in the left file
   * @param right file to extract changes
   * @param leftVersion version of the language in the right file
   */
  @SuppressWarnings("unchecked")
  public void extractClassifiedSourceCodeChanges(
      File left, String leftVersion, File right, String rightVersion) {
    super.extractClassifiedSourceCodeChanges(
        Content.fromFile(left, leftVersion), Content.fromFile(right, rightVersion));
  }

  public void extractClassifiedSourceCodeChanges(File left, File right, String version) {
    fVersion = version;
    super.extractClassifiedSourceCodeChanges(
        Content.fromFile(left, version), Content.fromFile(right, version));
  }
}
