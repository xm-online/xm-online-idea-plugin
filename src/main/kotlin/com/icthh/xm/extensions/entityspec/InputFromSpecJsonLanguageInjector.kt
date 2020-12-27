package com.icthh.xm.extensions.entityspec

import com.icthh.xm.utils.getChildrenByPath
import com.icthh.xm.utils.keyTextMatches
import com.icthh.xm.utils.start
import com.icthh.xm.utils.stop
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.groovy.GroovyLanguageInjectionSupport
import org.jetbrains.annotations.NotNull
import org.jetbrains.yaml.psi.*
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl
import java.util.*


class InputFromSpecJsonMultiHostInjector : MultiHostInjector {

    val language: Language? = Language.findLanguageByID("JSON")

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {

        start("MultiHostInjector-getLanguagesToInject")
        doWork(context, registrar)
        stop("MultiHostInjector-getLanguagesToInject")
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
        val jsonFields = listOf("dataSpec", "dataForm", "inputSpec", "inputForm")
        return jsonFields.firstOrNull { element.keyTextMatches(it) } != null
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(YAMLKeyValue::class.java)
    }

}



