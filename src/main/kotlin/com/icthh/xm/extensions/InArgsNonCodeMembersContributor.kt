package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.isConfigProject
import com.icthh.xm.utils.getCountSubstring
import com.intellij.codeInsight.AnnotationUtil.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import com.intellij.psi.util.ClassUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import java.util.*

val UNIQ_ID = UUID.randomUUID().toString()
const val LEP_ANNOTATION = "com.icthh.xm.commons.lep.LogicExtensionPoint"

class InArgsNonCodeMembersContributor: NonCodeMembersContributor() {

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

        val parent = place.parent
        val inArgs = "lepContext.inArgs"
        val lepName = place.originalFile.name.substringBefore("$$").replace("$", ".")

        if (!(aClass != null && aClass.getCountSubstring() > 0)) {
            return
        }

        if (parent != null && parent.text.startsWith(inArgs)) {
            val psiManager = PsiManager.getInstance(project)
            val annotationPsiClass = ClassUtil.findPsiClass(
                psiManager,
                LEP_ANNOTATION
            ) ?: return

            val annotatedPsiMethods = AnnotatedMembersSearch.search(annotationPsiClass).findAll()
                .filterIsInstance<PsiMethod>()

            annotatedPsiMethods.filter { isAnnotated(it, LEP_ANNOTATION, CHECK_TYPE) }
                .filter { "\"${lepName}\"" == findAnnotation(it, LEP_ANNOTATION)?.findAttributeValue("value")?.text }
                .forEach { method ->
                    val nameHint = processor.getHint(NameHint.KEY)?.getName(state)
                    if (nameHint == null) {
                        method.parameterList.parameters.forEach {
                            inject(it, processor, state)
                        }
                    } else {
                        method.parameterList.parameters.filter { it.name == nameHint }.forEach {
                            inject(it, processor, state)
                        }
                    }
                }
        }
    }

    private fun inject(
        parameter: PsiParameter,
        processor: PsiScopeProcessor,
        state: ResolveState
    ) {
        val param = LightFieldBuilder(parameter.name, parameter.type, parameter)
        processor.execute(param, state)
    }

}
