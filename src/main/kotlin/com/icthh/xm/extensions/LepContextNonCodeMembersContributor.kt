package com.icthh.xm.extensions


import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor


class LepContextNonCodeMembersContributor: NonCodeMembersContributor() {

    override fun processDynamicElements(
        qualifierType: PsiType,
        aClass: PsiClass?,
        processor: PsiScopeProcessor,
        place: PsiElement,
        state: ResolveState
    ) {
        val nameHint = processor.getHint(NameHint.KEY)
        if (aClass != null && aClass.getCountSubstring() > 1 && nameHint?.getName(state) == "lepContext") {
            val lepContext = LightFieldBuilder("lepContext", "com.icthh.xm.ms.entity.lep.LepContext", aClass)
            processor.execute(lepContext, state)
        }
    }

    private fun PsiClass.getCountSubstring(): Int {
        val search = "$$"
        var index = 0
        var count = 0
        while (index >= 0) {
            index = this.name?.indexOf(search, index + 1) ?: -1
            count++
        }
        return count
    }

}
