package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.isConfigProject
import com.icthh.xm.utils.getCountSubstring
import com.icthh.xm.utils.logger
import com.intellij.codeInsight.AnnotationUtil.*
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import com.intellij.psi.util.ClassUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import java.util.*
import kotlin.collections.HashMap

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

        if (parent != null && place.text.startsWith(inArgs)) {
            val psiManager = PsiManager.getInstance(project)
            val annotationPsiClass = ClassUtil.findPsiClass(
                psiManager,
                LEP_ANNOTATION
            ) ?: return

            val annotatedPsiMethods = AnnotatedMembersSearch.search(annotationPsiClass).findAll()
                .filterIsInstance<PsiMethod>()

            val lepFolder = getLepFolder(project, place)?.path
            val file = place.originalFile.virtualFile ?: return
            val relativePath = file.path.substringAfter(lepFolder ?: "")

            val fields = HashMap<String, PsiParameter>()
            annotatedPsiMethods.filter { isAnnotated(it, LEP_ANNOTATION, CHECK_TYPE) }
                .filter { lepName == findAnnotation(it, LEP_ANNOTATION)?.findAttributeValue("value")?.stringValue() }
                .filter {
                    relativePath.startsWith(getPackagePrefix(it))
                }
                .forEach { method ->
                    val nameHint = processor.getHint(NameHint.KEY)?.getName(state)
                    if (nameHint != null) {
                        method.parameterList.parameters.filter { it.name == nameHint }.forEach {
                            fields.put(it.name, it)
                        }
                    } else {
                        method.parameterList.parameters.forEach {
                            fields.put(it.name, it)
                        }
                    }
                }
            fields.forEach { inject(it.value, processor, state) }
        }
    }

    private fun getPackagePrefix(annotatedPsiMethod: PsiMethod): String {
        val LEP_SERVICE_ANNOTATION = "com.icthh.xm.commons.lep.spring.LepService"
        val packagePrefix =
            findAnnotation(annotatedPsiMethod.containingClass, LEP_SERVICE_ANNOTATION)?.findAttributeValue(
                "group"
            )?.stringValue()
        if (packagePrefix == "general" || packagePrefix == null) {
            return ""
        }
        val pathPrefix = packagePrefix.replace(".", "/")
        return "/$pathPrefix/"
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
