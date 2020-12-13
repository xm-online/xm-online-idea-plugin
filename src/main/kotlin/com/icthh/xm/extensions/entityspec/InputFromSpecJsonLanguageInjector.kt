package com.icthh.xm.extensions.entityspec

import com.icthh.xm.utils.start
import com.icthh.xm.utils.stop
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl


class InputFromSpecJsonLanguageInjector : MultiHostInjector {

    val language: Language? = Language.findLanguageByID("JSON")

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        start("getLanguagesToInject")
        doWork(context, registrar)
        stop("getLanguagesToInject")
    }

    private fun doWork(context: PsiElement, registrar: MultiHostRegistrar) {
        language ?: return

        if (context !is YAMLScalarImpl || !context.isEntitySpecification()) {
            return
        }

        val parent = context.getParent() as? YAMLKeyValueImpl ?: return

        val keyText = parent.keyText
        if (setOf(
                "dataSpec",
                "dataForm",
                "inputSpec",
                "inputForm"
            ).contains(keyText) && context.contentRanges.isNotEmpty()
        ) {
            val startPoint = context.contentRanges.first().startOffset
            val endPoint = context.contentRanges.last().endOffset
            registrar.startInjecting(language)
            val textRange = TextRange.from(startPoint, endPoint - startPoint)
            registrar.addPlace(
                null,
                null,
                context as PsiLanguageInjectionHost,
                textRange
            )
            registrar.doneInjecting()
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(YAMLScalar::class.java)
    }
}
