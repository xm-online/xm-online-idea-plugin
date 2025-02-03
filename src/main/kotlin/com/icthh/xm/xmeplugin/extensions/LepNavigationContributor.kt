package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.services.XmeProjectStateService
import com.icthh.xm.xmeplugin.utils.translateToLepConvention
import com.intellij.ide.util.gotoByName.DefaultFileNavigationContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter


open class LepChooseByNameContributor: ChooseByNameContributorEx {

    val delegate: ChooseByNameContributorEx = DefaultFileNavigationContributor()

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val service = project.service<XmeProjectStateService>()
        delegate.processNames({ name ->
            if (name.endsWith(".groovy")) {
                val transliterated = replaceDots(replaceDollars(name))
                processor.process(name)
                processor.process(replaceDots(name))
                processor.process(replaceDollars(name))
                processor.process(transliterated)
                processor.process(name)
            }
            val domains = service.getTenantDomains(name)
            domains.forEach { processor.process(it) }
            true
        }, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters
    ) {
        if (name.endsWith(".groovy")) {
            val completePattern = parameters.completePattern
            val transliteratedPattern = transliterate(completePattern)
            if (transliteratedPattern != completePattern) {
                val transliteratedName = transliterate(name.substringBeforeLast(".groovy")) + ".groovy"
                delegate.processElementsWithName(
                    transliteratedName,
                    processor,
                    newParametersWith(parameters, transliteratedPattern, transliteratedName)
                )
            }
            return
        }

        val service = parameters.project.service<XmeProjectStateService>()
        val tenants = service.getTenantByDomain(name)
        tenants.forEach {
            delegate.processElementsWithName(
                it.uppercase(),
                processor,
                newParametersWith(parameters, parameters.completePattern, it.uppercase())
            )
        }
    }

    private fun newParametersWith(parameters: FindSymbolParameters, pattern: String, name: String) =
        FindSymbolParameters(pattern, name, parameters.searchScope)

    private fun transliterate(symbol: String) = translateToLepConvention(symbol)

    private fun replaceDots(symbol: String) = symbol.replace("_".toRegex(), "-")

    private fun replaceDollars(symbol: String) = symbol.replace("\\$".toRegex(), "\\.")
}
