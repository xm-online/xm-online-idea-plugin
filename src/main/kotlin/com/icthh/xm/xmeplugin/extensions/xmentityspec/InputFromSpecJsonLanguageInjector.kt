package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.utils.keyTextMatches
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.ConcurrentHashMap
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl


class InputFromSpecJsonLanguageInjector : MultiHostInjector {

    private val languages = ConcurrentHashMap<String, Language?>()

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        doWork(context, registrar)
    }

    private fun doWork(context: PsiElement, registrar: MultiHostRegistrar) {

        if (context !is YAMLKeyValue) {
            return
        }
        val value = context.value
        if (value !is YAMLScalarImpl) {
            return
        }

        val xmePluginSpecService = context.project.xmePluginSpecService
        xmePluginSpecService.getSpecifications(context.containingFile).flatMap { it.injections }
            .filter { it.elementPath?.let { path -> xmePluginSpecService.parsePattern(path).accepts(value) }.isTrue }
            .forEach {
                if (value.contentRanges.isNotEmpty()) {
                    val startPoint = value.contentRanges.first().startOffset
                    val endPoint = value.contentRanges.last().endOffset
                    val textRange = TextRange.from(startPoint, endPoint - startPoint)
                    val lang = languages.getOrPut(it.language) {
                        Language.findLanguageByID(it.language)
                    } ?: return
                    registrar.startInjecting(lang).addPlace(null, null, value, textRange).doneInjecting()
                }
            }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(YAMLKeyValue::class.java)
    }

}



