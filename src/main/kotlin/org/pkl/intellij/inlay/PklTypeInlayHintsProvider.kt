/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.intellij.inlay

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import org.pkl.intellij.psi.*
import org.pkl.intellij.type.computeExprType
import org.pkl.intellij.type.computeResolvedImportType

class PklTypeInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
    PklTypeInlayHintsCollector

  private object PklTypeInlayHintsCollector : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      when (element) {
        is PklProperty -> collectProperty(element, sink)
        is PklTypedIdentifier -> collectTypedIdentifier(element, sink)
        is PklMethod -> collectMethod(element, sink)
      }
    }

    private fun PklProperty.isOverridingSuperProperty() =
      propertyName.reference?.resolve()?.let { it != this } == true

    private fun collectProperty(element: PklProperty, sink: InlayTreeSink) {
      // only show type hints for newly declared class properties (don't show hint if assigning
      // to/amending super property), or local object properties
      if (
        element.type != null ||
          element is PklObjectProperty && !element.isLocal ||
          element.isOverridingSuperProperty()
      )
        return
      val type =
        element.computeResolvedImportType(
          element.project.pklBaseModule,
          mapOf(),
          element.enclosingModule?.pklProject
        )
      sink.addPresentation(
        InlineInlayPosition(element.propertyName.identifier.textRange.endOffset, true),
        hasBackground = true
      ) {
        text(": ")
        text(type.render())
      }
    }

    // handler for function params, object body params, let exprs
    private fun collectTypedIdentifier(element: PklTypedIdentifier, sink: InlayTreeSink) {
      if (element.type != null || element.text == "_") return
      val type =
        element.computeResolvedImportType(
          element.project.pklBaseModule,
          mapOf(),
          element.enclosingModule?.pklProject
        )
      sink.addPresentation(
        InlineInlayPosition(element.identifier.textRange.endOffset, true),
        hasBackground = true
      ) {
        text(": ")
        text(type.render())
      }
    }

    private fun collectMethod(element: PklMethod, sink: InlayTreeSink) {
      if (element.returnType != null) return
      val parameterList = element.parameterList ?: return
      val type =
        element.body.computeExprType(
          element.project.pklBaseModule,
          mapOf(),
          element.enclosingModule?.pklProject
        )
      sink.addPresentation(
        InlineInlayPosition(parameterList.endOffset, true),
        hasBackground = true
      ) {
        text(": ")
        text(type.render())
      }
    }
  }
}
