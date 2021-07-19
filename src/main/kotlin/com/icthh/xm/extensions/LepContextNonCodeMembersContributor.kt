package com.icthh.xm.extensions


import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor


class LepContextNonCodeMembersContributor: NonCodeMembersContributor() {

    override fun processDynamicElements(
        qualifierType: PsiType,
        aClass: PsiClass?,
        processor: PsiScopeProcessor,
        place: PsiElement,
        state: ResolveState
    ) {
        val project = place.project
        val selected = project.getSettings().selected()
        selected ?: return
        if (selected.isConfigProject) {
            return
        }

        val nameHint = processor.getHint(NameHint.KEY)
        if (aClass != null && aClass.getCountSubstring() > 0 && nameHint?.getName(state) == "lepContext") {
            val candidates = PsiShortNamesCache.getInstance(project).getClassesByName("LepContext", ProjectScope.getProjectScope(project))
            if (candidates.size > 0) {
                candidates[0].qualifiedName?.let{
                    val lepContext = LightFieldBuilder("lepContext", it, aClass)
                    processor.execute(lepContext, state)
                }
            }
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
