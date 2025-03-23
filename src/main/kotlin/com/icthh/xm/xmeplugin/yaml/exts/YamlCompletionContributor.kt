package com.icthh.xm.xmeplugin.yaml.exts

import com.icthh.xm.xmeplugin.utils.isSupportProject
import com.icthh.xm.xmeplugin.yaml.findAllElements
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecMetaInfoService
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import getTenantName
import org.apache.commons.text.similarity.LevenshteinDistance
import org.jetbrains.yaml.psi.YAMLScalar
import runJsScriptWithResult
import runMessageTemplate


class YamlCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val input = parameters.position
        if (input !is LeafPsiElement) return

        val project = input.project
        if (!project.isSupportProject()) {
            return
        }

        val element = input.parent
        if (element !is YAMLScalar) {
            return
        }

        val tenantName = element.containingFile.originalFile.getTenantName()

        val xmePluginSpecService = project.xmePluginSpecService
        xmePluginSpecService.getSpecifications(element.containingFile.originalFile).forEach { spec ->
            ProgressManager.checkCanceled()
            val nodeContext = project.xmePluginSpecMetaInfoService.getYamlContext(element, spec.key)
            nodeContext ?: return@forEach
            ProgressManager.checkCanceled()

            spec.autocompletes.forEach { autoComplete ->
                ProgressManager.checkCanceled()
                val elementPath = autoComplete.elementPath ?: return@forEach
                val parsePattern = xmePluginSpecService.parsePattern(elementPath)
                if (parsePattern.accepts(element)) {
                    var completeVariants = mutableListOf<String>();

                    autoComplete.variants?.forEach { completeVariants.add(it) }

                    ProgressManager.checkCanceled()
                    autoComplete.variantsPath?.let {
                        val path = runMessageTemplate(
                            nodeContext,
                            autoComplete.variantsPath,
                            autoComplete.includeFunctions,
                            project
                        ) ?: return@let
                        val pathPattern = xmePluginSpecService.parsePattern(path)
                        element.project.xmePluginSpecService.getFiles(tenantName, spec.key).flatMap { file ->
                            findAllElements(file, pathPattern)
                        }.forEach {
                            completeVariants.add(it.text)
                        }
                    }

                    ProgressManager.checkCanceled()
                    autoComplete.variantsExpression?.let {
                        val variants = runJsScriptWithResult(
                            nodeContext,
                            it,
                            autoComplete.includeFunctions,
                            project
                        ) ?: return@let
                        if (variants is Iterable<*>) {
                            variants.forEach { completeVariants.add(it.toString()) }
                        } else {
                            completeVariants.add(variants.toString())
                        }
                    }

                    val prefix = result.prefixMatcher.prefix.lowercase()
                    var results = result.withPrefixMatcher(CamelHumpMatcher(prefix, false))
                    completeVariants.forEach { results.addElement(LookupElementBuilder.create(it)) }
                }
            }
        }

        result.stopHere()
    }

}
