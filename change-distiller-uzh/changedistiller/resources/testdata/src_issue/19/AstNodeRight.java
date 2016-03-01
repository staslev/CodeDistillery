/**
 * ***************************************************************************** Copyright (c) 2000,
 * 2001, 2002 International Business Machines Corp. and others. All rights reserved. This program
 * and the accompanying materials are made available under the terms of the Common Public License
 * v0.5 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 *
 * <p>Contributors: IBM Corporation - initial API and implementation
 * ****************************************************************************
 */
package org.eclipse.jdt.internal.compiler.ast;

import org.eclipse.jdt.internal.compiler.IAbstractSyntaxTreeVisitor;

public abstract class AstNode implements BaseTypes, CompilerModifiers, TypeConstants, TypeIds {

  // some global provision for the hierarchy
  public static final Constant NotAConstant = Constant.NotAConstant;
  // storage for internal flags (32 bits)			BIT USAGE
  public static final int Bit1 =
      0x1; // return type (operators) | name reference kind (name ref) | add assertion (type decl)
  public static final int Bit2 =
      0x2; // return type (operators) | name reference kind (name ref) | has local type (type,
  // method, field decl)
  public static final int Bit3 =
      0x4; // return type (operators) | name reference kind (name ref) | implicit this (this ref)
  public static final int Bit4 =
      0x8; // return type (operators) | first assignment to local (local decl)
  public static final int Bit5 = 0x10; // value for return (binary expression)
  public static final int Bit6 =
      0x20; // depth (name ref, msg) | only value required (binary expression)
  public static final int Bit7 = 0x40; // depth (name ref, msg) | operator (operators)
  public static final int Bit8 = 0x80; // depth (name ref, msg) | operator (operators)
  public static final int Bit9 = 0x100; // depth (name ref, msg) | operator (operators)
  public static final int Bit10 = 0x200; // depth (name ref, msg) | operator (operators)
  public static final int Bit11 = 0x400; // depth (name ref, msg) | operator (operators)
  public static final int Bit12 = 0x800; // depth (name ref, msg) | operator (operators)
  public static final int Bit13 = 0x1000; // depth (name ref, msg)
  public static final int Bit14 = 0x2000;
  public static final int Bit15 = 0x4000;
  public static final int Bit16 = 0x8000;
  public static final int Bit17 = 0x10000;
  public static final int Bit18 = 0x20000;
  public static final int Bit19 = 0x40000;
  public static final int Bit20 = 0x80000;
  public static final int Bit21 = 0x100000;
  public static final int Bit22 = 0x200000; // parenthesis count (expression)
  public static final int Bit23 = 0x400000; // parenthesis count (expression)
  public static final int Bit24 = 0x800000; // parenthesis count (expression)
  public static final int Bit25 = 0x1000000; // parenthesis count (expression)
  public static final int Bit26 = 0x2000000; // parenthesis count (expression)
  public static final int Bit27 = 0x4000000; // parenthesis count (expression)
  public static final int Bit28 = 0x8000000; // parenthesis count (expression)
  public static final int Bit29 = 0x10000000; // parenthesis count (expression)
  public static final int Bit30 = 0x20000000; // assignment with no effect (assignment)
  public static final int Bit31 = 0x40000000; // local declaration reachable (local decl)
  public static final int Bit32 = 0x80000000; // reachable (statement)
  // for operators
  public static final int ReturnTypeIDMASK = Bit1 | Bit2 | Bit3 | Bit4;
  public static final int OperatorSHIFT = 6; // Bit7 -> Bit12
  public static final int OperatorMASK =
      Bit7 | Bit8 | Bit9 | Bit10 | Bit11 | Bit12; // 6 bits for operator ID
  // for binary expressions
  public static final int ValueForReturnMASK = Bit5;
  public static final int OnlyValueRequiredMASK = Bit6;
  // for name references
  public static final int RestrictiveFlagMASK = Bit1 | Bit2 | Bit3;
  public static final int FirstAssignmentToLocalMASK = Bit4;
  // for this reference
  public static final int IsImplicitThisMask = Bit3;
  // for single name references
  public static final int DepthSHIFT = 5; // Bit6 -> Bit13
  public static final int DepthMASK =
      Bit6 | Bit7 | Bit8 | Bit9 | Bit10 | Bit11 | Bit12
          | Bit13; // 8 bits for actual depth value (max. 255)
  // for statements
  public static final int IsReachableMASK = Bit32;
  public static final int IsLocalDeclarationReachableMASK = Bit31;
  // for type declaration
  public static final int AddAssertionMASK = Bit1;
  // for type, method and field declarations
  public static final int HasLocalTypeMASK = Bit2; // cannot conflict with AddAssertionMASK
  // for expression
  public static final int ParenthesizedSHIFT = 21; // Bit22 -> Bit29
  public static final int ParenthesizedMASK =
      Bit22 | Bit23 | Bit24 | Bit25 | Bit26 | Bit27 | Bit28
          | Bit29; // 8 bits for parenthesis count value (max. 255)
  // for assignment
  public static final int IsAssignmentWithNoEffectMASK = Bit30;
  public int sourceStart, sourceEnd;
  public int bits = IsReachableMASK; // reachable by default

  public AstNode() {

    super();
  }

  public static String modifiersString(int modifiers) {

    String s = ""; // $NON-NLS-1$
    if ((modifiers & AccPublic) != 0) s = s + "public "; // $NON-NLS-1$
    if ((modifiers & AccPrivate) != 0) s = s + "private "; // $NON-NLS-1$
    if ((modifiers & AccProtected) != 0) s = s + "protected "; // $NON-NLS-1$
    if ((modifiers & AccStatic) != 0) s = s + "static "; // $NON-NLS-1$
    if ((modifiers & AccFinal) != 0) s = s + "final "; // $NON-NLS-1$
    if ((modifiers & AccSynchronized) != 0) s = s + "synchronized "; // $NON-NLS-1$
    if ((modifiers & AccVolatile) != 0) s = s + "volatile "; // $NON-NLS-1$
    if ((modifiers & AccTransient) != 0) s = s + "transient "; // $NON-NLS-1$
    if ((modifiers & AccNative) != 0) s = s + "native "; // $NON-NLS-1$
    if ((modifiers & AccAbstract) != 0) s = s + "abstract "; // $NON-NLS-1$
    return s;
  }

  public static String tabString(int tab) {

    String s = ""; // $NON-NLS-1$
    for (int i = tab; i > 0; i--) s = s + "  "; // $NON-NLS-1$
    return s;
  }

  public boolean cannotReturn() {
    return false;
  }

  public AstNode concreteStatement() {
    return this;
  }

  /* Answer true if the field use is considered deprecated.
   * An access in the same compilation unit is allowed.
   */
  public final boolean isFieldUseDeprecated(FieldBinding field, Scope scope) {

    if (!field.isViewedAsDeprecated()) return false;

    // inside same unit - no report
    if (scope.isDefinedInSameUnit(field.declaringClass)) return false;

    // if context is deprecated, may avoid reporting
    if (!scope.environment().options.reportDeprecationInsideDeprecatedCode
        && scope.isInsideDeprecatedCode()) return false;
    return true;
  }

  public boolean isImplicitThis() {

    return false;
  }

  /* Answer true if the method use is considered deprecated.
   * An access in the same compilation unit is allowed.
   */
  public final boolean isMethodUseDeprecated(MethodBinding method, Scope scope) {

    if (!method.isViewedAsDeprecated()) return false;

    // inside same unit - no report
    if (scope.isDefinedInSameUnit(method.declaringClass)) return false;

    // if context is deprecated, may avoid reporting
    if (!scope.environment().options.reportDeprecationInsideDeprecatedCode
        && scope.isInsideDeprecatedCode()) return false;
    return true;
  }

  public boolean isSuper() {

    return false;
  }

  public boolean isThis() {

    return false;
  }

  /* Answer true if the type use is considered deprecated.
   * An access in the same compilation unit is allowed.
   */
  public final boolean isTypeUseDeprecated(TypeBinding type, Scope scope) {

    if (type.isArrayType()) type = ((ArrayBinding) type).leafComponentType;
    if (type.isBaseType()) return false;

    ReferenceBinding refType = (ReferenceBinding) type;
    if (!refType.isViewedAsDeprecated()) return false;

    // inside same unit - no report
    if (scope.isDefinedInSameUnit(refType)) return false;

    // if context is deprecated, may avoid reporting
    if (!scope.environment().options.reportDeprecationInsideDeprecatedCode
        && scope.isInsideDeprecatedCode()) return false;
    return true;
  }

  /** @deprecated - use field instead */
  public int sourceEnd() {
    return sourceEnd;
  }

  /** @deprecated - use field instead */
  public int sourceStart() {
    return sourceStart;
  }

  public String toString() {

    return toString(0);
  }

  public String toString(int tab) {

    return "****" + super.toString() + "****"; // $NON-NLS-2$ //$NON-NLS-1$
  }

  public void traverse(IAbstractSyntaxTreeVisitor visitor, BlockScope scope) {}
}
