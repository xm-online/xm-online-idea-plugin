package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.utils.keyTextMatches
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl


class InputFromSpecJsonLanguageInjector : MultiHostInjector {

    val language: Language? = Language.findLanguageByID("JSON")

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        doWork(context, registrar)
    }

    private fun doWork(context: PsiElement, registrar: MultiHostRegistrar) {
        language ?: return

        if (context !is YAMLKeyValue || !context.isEntitySpecification()) {
            return
        }

        val value = context.value
        if (value !is YAMLScalarImpl) {
            return
        }

        if (isSpecs(context) && value.contentRanges.isNotEmpty()) {
            val startPoint = value.contentRanges.first().startOffset
            val endPoint = value.contentRanges.last().endOffset
            val textRange = TextRange.from(startPoint, endPoint - startPoint)
            registrar.startInjecting(language).addPlace(null, null, value, textRange).doneInjecting()
        }
    }

    private fun isSpecs(element: YAMLKeyValue): Boolean {
        val jsonFields = listOf("dataSpec", "dataForm", "inputSpec", "inputForm", "contextDataSpec", "contextDataForm", "value")
        return jsonFields.firstOrNull { element.keyTextMatches(it) } != null
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(YAMLKeyValue::class.java)
    }

}



