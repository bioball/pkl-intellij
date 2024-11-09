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
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlin.math.min
import org.pkl.intellij.psi.*
import org.pkl.intellij.type.toType

class PklParameterNameInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
    ParameterNameInlayHintsProvider

  object ParameterNameInlayHintsProvider : SharedBypassCollector {
    // assumption: the only external "apply" method is inside a `Function`.
    private val PklMethod.isFunctionApplyMethod: Boolean
      get() = isExternal && name == "apply"

    private fun PsiElement.resolveFunctionLiteral(): PklFunctionLiteral? {
      return RecursionManager.doPreventingRecursion(this, false) {
        when (this) {
          is PklProperty -> expr?.resolveFunctionLiteral()
          is PklFunctionLiteral -> this
          is PklAccessExpr -> memberName.reference?.resolve()?.resolveFunctionLiteral()
          is PklLetExpr -> bodyExpr?.resolveFunctionLiteral()
          is PklIfExpr -> thenExpr?.resolveFunctionLiteral() ?: elseExpr?.resolveFunctionLiteral()
          else -> null
        }
      }
    }

    private fun getParameterList(element: PklAccessExpr): PklParameterList? {
      val method = element.memberName.reference?.resolve() as? PklMethod ?: return null
      if (method.isFunctionApplyMethod) {
        val qualifiedAccess = element as? PklQualifiedAccessExpr
        return (qualifiedAccess?.receiverExpr as? PklAccessExpr)
          ?.memberName
          ?.reference
          ?.resolve()
          ?.resolveFunctionLiteral()
          ?.parameterList
      }
      return method.parameterList
    }

    private fun PklAccessExpr.isMethodOffPrimitive() =
      this is PklQualifiedAccessExpr &&
        when (receiverExpr) {
          is PklStringLiteral,
          is PklTrueLiteral,
          is PklFalseLiteral,
          is PklNullLiteral,
          is PklNumberLiteral -> true
          else -> false
        }

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element !is PklAccessExpr) return
      val base = element.project.pklBaseModule
      val context = element.enclosingModule?.pklProject
      val argumentList = element.argumentList ?: return
      val parameterList = getParameterList(element) ?: return
      for (i in 0 until min(argumentList.elements.size, parameterList.elements.size)) {
        val arg = argumentList.elements[i]
        // omit hint if argument is named already, and also omit methods off primitives
        // (for example, treat `5.d` as a primitive)
        if (arg is PklAccessExpr && !arg.isMethodOffPrimitive()) {
          continue
        }
        val param = parameterList.elements[i]
        val paramName = param.name ?: continue
        val paramType = param.type?.toType(base, mapOf(), context)
        // don't show inlay for varargs
        if (paramType?.isSubtypeOf(base.varArgsType, base, context) == true) {
          return
        }
        sink.addPresentation(
          InlineInlayPosition(arg.textRange.startOffset, true),
          hasBackground = true,
        ) {
          text(paramName)
          text(": ")
        }
      }
    }
  }
}
