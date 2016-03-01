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

package com.staslev.codedistillery.distilling.changes.uzh

object FineGrainedChanges {
  val all: List[String] = List(
    "ADDING_ATTRIBUTE_MODIFIABILITY",
    "ADDING_CLASS_DERIVABILITY",
    "ADDING_METHOD_OVERRIDABILITY",
    "ADDITIONAL_CLASS",
    "ADDITIONAL_FUNCTIONALITY",
    "ADDITIONAL_OBJECT_STATE",
    "ALTERNATIVE_PART_DELETE",
    "ALTERNATIVE_PART_INSERT",
    "ATTRIBUTE_RENAMING",
    "ATTRIBUTE_TYPE_CHANGE",
    "CLASS_RENAMING",
    "COMMENT_DELETE",
    "COMMENT_INSERT",
    "COMMENT_MOVE",
    "COMMENT_UPDATE",
    "CONDITION_EXPRESSION_CHANGE",
    "DECREASING_ACCESSIBILITY_CHANGE",
    "DOC_DELETE",
    "DOC_INSERT",
    "DOC_UPDATE",
    "INCREASING_ACCESSIBILITY_CHANGE",
    "METHOD_RENAMING",
    "PARAMETER_DELETE",
    "PARAMETER_INSERT",
    "PARAMETER_ORDERING_CHANGE",
    "PARAMETER_RENAMING",
    "PARAMETER_TYPE_CHANGE",
    "PARENT_CLASS_CHANGE",
    "PARENT_CLASS_DELETE",
    "PARENT_CLASS_INSERT",
    "PARENT_INTERFACE_CHANGE",
    "PARENT_INTERFACE_DELETE",
    "PARENT_INTERFACE_INSERT",
    "REMOVED_CLASS",
    "REMOVED_FUNCTIONALITY",
    "REMOVED_OBJECT_STATE",
    "REMOVING_ATTRIBUTE_MODIFIABILITY",
    "REMOVING_CLASS_DERIVABILITY",
    "REMOVING_METHOD_OVERRIDABILITY",
    "RETURN_TYPE_CHANGE",
    "RETURN_TYPE_DELETE",
    "RETURN_TYPE_INSERT",
    "STATEMENT_DELETE",
    "STATEMENT_INSERT",
    "STATEMENT_ORDERING_CHANGE",
    "STATEMENT_PARENT_CHANGE",
    "STATEMENT_UPDATE",
    "UNCLASSIFIED_CHANGE"
  ).sorted
}
