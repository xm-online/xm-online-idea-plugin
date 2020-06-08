package com.icthh.xm.extensions.entityspec

import com.icthh.xm.utils.logger
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.jetbrains.jsonSchema.impl.JsonSchemaBasedLanguageInjector
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl


class InputFromSpecJsonLanguageInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {

        if (context !is YAMLScalarImpl) {
            return
        }

        if (!context.isEntitySpecification()) {
            return
        }

        val parent = context.getParent() as? YAMLKeyValueImpl ?: return

        val keyText = parent.keyText
        if (setOf("dataSpec", "dataForm", "inputSpec", "inputForm").contains(keyText) && context.contentRanges.isNotEmpty()) {
            val language = Language.findLanguageByID("JSON") ?: return
            registrar.startInjecting(language)
            val startPoint = context.contentRanges.first().startOffset
            val endPoint = context.contentRanges.last().endOffset
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
