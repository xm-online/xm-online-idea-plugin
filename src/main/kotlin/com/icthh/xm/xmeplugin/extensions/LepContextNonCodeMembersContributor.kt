package com.icthh.xm.xmeplugin.extensions


import com.icthh.xm.xmeplugin.utils.getCountSubstring
import com.icthh.xm.xmeplugin.utils.getSettings
import com.icthh.xm.xmeplugin.utils.isConfigProject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
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
        if (project.isConfigProject()) {
            return
        }

        val nameHint = processor.getHint(NameHint.KEY)
        if (aClass != null && aClass.getCountSubstring() > 0 && nameHint?.getName(state) == "lepContext") {
            injectLepContext(project, aClass, processor, state)
        }

        if (aClass != null && nameHint == null && place.parent !is GrReferenceExpression && aClass is GrTypeDefinition) {
            injectLepContext(project, aClass, processor, state)
        }
    }

    private fun injectLepContext(
        project: Project,
        aClass: PsiClass,
        processor: PsiScopeProcessor,
        state: ResolveState
    ) {
        val scope = ProjectScope.getProjectScope(project)
        val candidates = PsiShortNamesCache.getInstance(project)
            .getClassesByName("LepContext", scope)
        if (candidates.size > 0) {
            candidates[0].qualifiedName?.let {
                val lepContext = LightFieldBuilder("lepContext", it, aClass)
                processor.execute(lepContext, state)
            }
        }
    }

}
