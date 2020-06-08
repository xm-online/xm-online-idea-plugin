package com.icthh.xm.extensions

import com.icthh.xm.utils.logger
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext


class CommonsCompletionContributor: CompletionContributor() {
    init {

        /*if the parameter position is an xml attribute provide attributes using given xsd*/
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                public override fun addCompletions(
                    parameters: CompletionParameters, //completion parameters contain details of the curser position
                    context: ProcessingContext,
                    resultSet: CompletionResultSet
                ) {
                    logger.info("${parameters.position.parent.text}")
                    logger.info("${context}")
                }
            }
        )

    }
}