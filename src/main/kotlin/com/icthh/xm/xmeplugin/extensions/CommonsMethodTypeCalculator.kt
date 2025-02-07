package com.icthh.xm.xmeplugin.extensions

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall


class LepMethodCompletionContributor : CompletionContributor() {
  init {
    // This example uses a PSI pattern for Groovy method calls.
    extend(
      CompletionType.BASIC,
      psiElement().withParent(GrMethodCall::class.java),
      object : CompletionProvider<CompletionParameters?>() {
        override fun addCompletions(
          parameters: CompletionParameters,
          context: ProcessingContext,
          result: CompletionResultSet
        ) {
          result.addElement(
            LookupElementBuilder.create("methodCall")
              .withTypeText("java.lang.String")
              .withTailText("(Integer a, Boolean b)", true)
          )
        }
      })
  }
}
