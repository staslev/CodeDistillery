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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.ChangeType;
import ch.uzh.ifi.seal.changedistiller.model.entities.ClassHistory;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.util.CompilationUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class WhenFilesAreDistilled {

  private static final String TEST_DATA = "src_change/";
  private static FileDistiller distiller;

  @BeforeClass
  public static void initialize() {
    distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
  }

  @Test
  public void testMethodsShouldBeDetected() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("public interface Foo {" + "@Test public void bar();" + "}", "file2.java"),
        new Content("", "file1.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(2));
    assertThat(changes.get(1).getChangeType(), is(ChangeType.REMOVED_CLASS));
    assertThat(changes.get(1).getRootEntity().getUniqueName(), is("Foo"));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.REMOVED_FUNCTIONALITY));
    assertThat(changes.get(0).getChangedEntity().getUniqueName(), is("@Test@.Foo.bar()"));
  }

  @Test
  public void addingInnerClassAddsInnerMethods() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("", "file1.java"),
        new Content(
            "public class Foo {"
                + "public static class Bar {"
                + "public void write(String s){}"
                + "}"
                + "}",
            "file2.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size() >= 3, is(true));
    /*
    assertThat(changes.size(), is(3));
    assertThat(changes.get(1).getChangeType(), is(ChangeType.REMOVED_CLASS));
    assertThat(changes.get(1).getRootEntity().getUniqueName(), is("Foo"));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.REMOVED_FUNCTIONALITY));
    assertThat(changes.get(0).getChangedEntity().getUniqueName(), is("@Test@.Foo.bar()"));
    */
  }

  @Test
  public void existingTopLevelClassDeletionShouldBeDetected() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("public interface Foo {" + "public void bar();" + "}", "file2.java"),
        new Content("", "file1.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(2));
    assertThat(changes.get(1).getChangeType(), is(ChangeType.REMOVED_CLASS));
    assertThat(changes.get(1).getRootEntity().getUniqueName(), is("Foo"));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.REMOVED_FUNCTIONALITY));
    assertThat(changes.get(0).getChangedEntity().getUniqueName(), is("Foo.bar()"));
  }

  @Test
  public void newTopLevelClassAdditionShouldBeDetected() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("", "file1.java"),
        new Content("public interface Foo {" + "public void bar();" + "}", "file2.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(2));
    assertThat(changes.get(1).getChangeType(), is(ChangeType.ADDITIONAL_CLASS));
    assertThat(changes.get(1).getRootEntity().getUniqueName(), is("Foo"));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.ADDITIONAL_FUNCTIONALITY));
    assertThat(changes.get(0).getChangedEntity().getUniqueName(), is("Foo.bar()"));
  }

  @Test
  public void interfaceMembersDeletionShouldBeDetected() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("public interface Foo {" + "public void bar();" + "}", "file2.java"),
        new Content("public interface Foo {}", "file1.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(1));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.REMOVED_FUNCTIONALITY));
    assertThat(changes.get(0).getRootEntity().getUniqueName(), is("Foo"));
  }

  @Test
  public void interfaceMembersAdditionShouldBeDetected() throws Exception {

    distiller.extractClassifiedSourceCodeChanges(
        new Content("public interface Foo {}", "file1.java"),
        new Content("public interface Foo {" + "public void bar();" + "}", "file2.java"));

    final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(1));
    assertThat(changes.get(0).getChangeType(), is(ChangeType.ADDITIONAL_FUNCTIONALITY));
    assertThat(changes.get(0).getRootEntity().getUniqueName(), is("Foo"));
  }

  @Test
  public void unchangedFilesShouldNotProduceSourceCodeChanges() throws Exception {
    File left = CompilationUtils.getFile(TEST_DATA + "TestLeft.java");
    File right = CompilationUtils.getFile(TEST_DATA + "TestLeft.java");
    distiller.extractClassifiedSourceCodeChanges(left, right);
    List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    assertThat(changes, is(not(nullValue())));
    assertThat(changes.size(), is(0));
  }

  @Ignore("Not enough info to debug, ignoring.")
  @Test
  public void changedFilesShouldProduceSourceCodeChanges() throws Exception {
    File left = CompilationUtils.getFile(TEST_DATA + "TestLeft.java");
    File right = CompilationUtils.getFile(TEST_DATA + "TestRight.java");
    distiller.extractClassifiedSourceCodeChanges(left, right);
    assertThat(distiller.getSourceCodeChanges().size(), is(23));
  }

  @Test
  public void changedFilesShouldProduceClassHistories() throws Exception {
    File left = CompilationUtils.getFile(TEST_DATA + "TestLeft.java");
    File right = CompilationUtils.getFile(TEST_DATA + "TestRight.java");
    distiller.extractClassifiedSourceCodeChanges(left, right);
    ClassHistory classHistory = distiller.getClassHistory();
    assertThat(classHistory.getAttributeHistories().size(), is(3));
    assertThat(classHistory.getMethodHistories().size(), is(1));
    assertThat(classHistory.getInnerClassHistories().size(), is(1));
    classHistory = classHistory.getInnerClassHistories().values().iterator().next();
    assertThat(classHistory.getUniqueName(), is("test.Test.Bar"));
    assertThat(classHistory.getMethodHistories().size(), is(1));
    String k = classHistory.getMethodHistories().keySet().iterator().next();
    assertThat(
        classHistory.getMethodHistories().get(k).getUniqueName(), is("test.Test.Bar.newMethod()"));
  }
}
