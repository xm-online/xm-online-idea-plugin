package com.icthh.xm.xmeplugin.yaml.exts

import com.icthh.xm.xmeplugin.utils.invokeLater
import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.psi.PsiElement
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl

class YamlLanguageInjectionContributor: LanguageInjectionContributor {

    override fun getInjection(value: PsiElement): Injection? {
        if (value !is YAMLScalarImpl) {
            return null
        }

        val context = value.parent
        if (context !is YAMLKeyValue) {
            return null
        }

        val xmePluginSpecService = context.project.xmePluginSpecService
        val injection = xmePluginSpecService.getSpecifications(context.containingFile)
            .flatMap { it.injections }
            .find { it.elementPath?.let { path -> xmePluginSpecService.parsePattern(path).accepts(value) }.isTrue }
        injection ?: return null
        val language = injection.language ?: return null
        val baseInjection = BaseInjection("comment")
        baseInjection.prefix = "";
        baseInjection.suffix = "";
        baseInjection.injectedLanguageId = language
        baseInjection.displayName = "language=${injection.language}";

        invokeLater {
            if (value.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE) == null) {
                TemporaryPlacesRegistry.getInstance(value.getProject())
                    .addHostWithUndo(value, InjectedLanguage.create(language))
            }
        }

        return baseInjection
    }

}



