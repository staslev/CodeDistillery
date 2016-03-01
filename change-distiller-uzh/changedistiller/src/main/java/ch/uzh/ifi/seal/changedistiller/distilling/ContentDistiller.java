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

import ch.uzh.ifi.seal.changedistiller.ast.ASTHelper;
import ch.uzh.ifi.seal.changedistiller.ast.ASTHelperFactory;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidateProcessor;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.ChangeType;
import ch.uzh.ifi.seal.changedistiller.model.entities.ClassHistory;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDiffNode;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDifferencer;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureNode;
import com.google.inject.Inject;
import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.TypeDeclaration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Distills {@link SourceCodeChange}s between two sources.
 *
 * @author Beat Fluri
 * @author Giacomo Ghezzi
 */
public class ContentDistiller {

  protected DistillerFactory fDistillerFactory;
  protected ASTHelperFactory fASTHelperFactory;
  protected RefactoringCandidateProcessor fRefactoringProcessor;

  protected List<SourceCodeChange> fChanges;
  protected ASTHelper<StructureNode> fLeftASTHelper;
  protected ASTHelper<StructureNode> fRightASTHelper;
  protected ClassHistory fClassHistory;
  protected String fVersion;

  @Inject
  ContentDistiller(
      DistillerFactory distillerFactory,
      ASTHelperFactory factory,
      RefactoringCandidateProcessor refactoringProcessor) {
    fDistillerFactory = distillerFactory;
    fASTHelperFactory = factory;
    fRefactoringProcessor = refactoringProcessor;
  }

  /**
   * Extracts classified {@link SourceCodeChange}s between two sources.
   *
   * @param left file to extract changes
   * @param right file to extract changes
   */
  public void extractClassifiedSourceCodeChanges(final Content left, final Content right) {

    Content effectiveLeft = left;
    Content effectiveRight = right;

    if (left.getContent().equals("")) {
      effectiveLeft = replaceEmptyContent(left, right);
    } else if (right.getContent().equals("")) {
      effectiveRight = replaceEmptyContent(right, left);
    }

    fLeftASTHelper = fASTHelperFactory.create(effectiveLeft, effectiveLeft.getContentLangVersion());

    fRightASTHelper =
        fASTHelperFactory.create(effectiveRight, effectiveRight.getContentLangVersion());

    extractDifferences();

    final ArrayList<SourceCodeChange> topLevelTypeChange = extractTopLevelTypeChange(left, right);

    if (topLevelTypeChange.size() > 0) {
      fChanges.addAll(topLevelTypeChange);
    }
  }

  private ArrayList<SourceCodeChange> extractTopLevelTypeChange(
      final Content left, final Content right) {
    if (left.getContent().equals("") || right.getContent().equals("")) {
      final StructureDifferencer structureDifferencer = new StructureDifferencer();
      structureDifferencer.extractDifferences(
          fLeftASTHelper.createStructureTree(), fRightASTHelper.createStructureTree());

      final StructureDiffNode structureDiff = structureDifferencer.getDifferences();
      // the top level type is assumed to be existing, otherwise this will throw an exception
      final StructureDiffNode structureDiffNode = structureDiff.getChildren().get(0);

      final SourceCodeChange more;

      if (left.getContent().equals("")) {
        more =
            new Insert(
                ChangeType.ADDITIONAL_CLASS,
                fRightASTHelper.createStructureEntityVersion(structureDiffNode.getRight()),
                fRightASTHelper.createSourceCodeEntity(structureDiffNode.getRight()),
                fRightASTHelper.createSourceCodeEntity(structureDiffNode.getRight()));
      } else {
        more =
            new Delete(
                ChangeType.REMOVED_CLASS,
                fRightASTHelper.createStructureEntityVersion(structureDiffNode.getLeft()),
                fRightASTHelper.createSourceCodeEntity(structureDiffNode.getLeft()),
                fRightASTHelper.createSourceCodeEntity(structureDiffNode.getLeft()));
      }

      return new ArrayList<SourceCodeChange>() {
        {
          add(more);
        }
      };
    } else {
      return new ArrayList<>();
    }
  }

  private Content replaceEmptyContent(Content left, Content right) {
    if (left.getContent().equals("")) {
      final TypeDeclaration typeDeclaration;
      try {
        typeDeclaration =
            JavaParser.parse(new StringReader(right.getContent()), false).getTypes().get(0);
      } catch (final ParseException e) {
        throw new RuntimeException("Failed parsing " + right.getName(), e);
      }
      typeDeclaration.setMembers(new LinkedList<BodyDeclaration>());
      left = new Content(typeDeclaration.toString(), left.getName());
    }
    return left;
  }

  private void extractDifferences() {
    StructureDifferencer structureDifferencer = new StructureDifferencer();
    structureDifferencer.extractDifferences(
        fLeftASTHelper.createStructureTree(), fRightASTHelper.createStructureTree());
    StructureDiffNode structureDiff = structureDifferencer.getDifferences();
    if (structureDiff != null) {
      fChanges = new LinkedList<SourceCodeChange>();
      // first node is (usually) the compilation unit
      processRootChildren(structureDiff);
    } else {
      fChanges = Collections.emptyList();
    }
  }

  private void processRootChildren(StructureDiffNode diffNode) {
    for (StructureDiffNode child : diffNode.getChildren()) {
      if (child.isClassOrInterfaceDiffNode() && mayHaveChanges(child.getLeft(), child.getRight())) {
        if (fClassHistory == null) {
          if (fVersion != null) {
            fClassHistory =
                new ClassHistory(
                    fRightASTHelper.createStructureEntityVersion(child.getRight(), fVersion));
          } else {
            fClassHistory =
                new ClassHistory(fRightASTHelper.createStructureEntityVersion(child.getRight()));
          }
        }
        processClassDiffNode(child);
      }
    }
  }

  private void processClassDiffNode(StructureDiffNode child) {
    ClassDistiller classDistiller;
    if (fVersion != null) {
      classDistiller =
          new ClassDistiller(
              child,
              fClassHistory,
              fLeftASTHelper,
              fRightASTHelper,
              fRefactoringProcessor,
              fDistillerFactory,
              fVersion);
    } else {
      classDistiller =
          new ClassDistiller(
              child,
              fClassHistory,
              fLeftASTHelper,
              fRightASTHelper,
              fRefactoringProcessor,
              fDistillerFactory);
    }
    classDistiller.extractChanges();
    fChanges.addAll(classDistiller.getSourceCodeChanges());
  }

  private boolean mayHaveChanges(StructureNode left, StructureNode right) {
    return (left != null) && (right != null);
  }

  public List<SourceCodeChange> getSourceCodeChanges() {
    return fChanges;
  }

  public ClassHistory getClassHistory() {
    return fClassHistory;
  }
}
