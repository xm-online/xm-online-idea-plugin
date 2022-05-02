package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.translateToLepConvention
import com.intellij.ide.util.gotoByName.DefaultFileNavigationContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ChooseByNameContributorEx2
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter


open class LepChooseByNameContributor: ChooseByNameContributorEx2 {

    val delegate: ChooseByNameContributorEx = DefaultFileNavigationContributor()

    override fun processNames(processor: Processor<in String>, parameters: FindSymbolParameters) {
        delegate.processNames({ name ->
            if (name.endsWith(".groovy")) {
                val transliterated = replaceDots(replaceDollars(name))
                processor.process(name)
                processor.process(replaceDots(name))
                processor.process(replaceDollars(name))
                processor.process(transliterated)
                processor.process(name)
            }
            true
        }, parameters.searchScope, parameters.idFilter)
    }

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        delegate.processNames({ name ->
            if (name.endsWith(".groovy")) {
                val transliterated = replaceDots(replaceDollars(name))
                processor.process(name)
                processor.process(replaceDots(name))
                processor.process(replaceDollars(name))
                processor.process(transliterated)
                processor.process(name)
            }
            true
        }, scope, filter)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters
    ) {
        val completePattern = parameters.completePattern

        val transliteratedPattern = transliterate(completePattern)
        if (transliteratedPattern != completePattern) {
            val transliteratedName = transliterate(name.substringBeforeLast(".groovy")) + ".groovy"
            delegate.processElementsWithName(
                transliteratedName, processor, newParametersWith(parameters, transliteratedPattern, transliteratedName)
            )
        }
    }

    private fun newParametersWith(parameters: FindSymbolParameters, pattern: String, name: String) =
        FindSymbolParameters(pattern, name, parameters.searchScope)

    private fun transliterate(symbol: String) = translateToLepConvention(symbol)

    private fun replaceDots(symbol: String) = symbol.replace("_".toRegex(), "-")

    private fun replaceDollars(symbol: String) = symbol.replace("\\$".toRegex(), "\\.")
}
