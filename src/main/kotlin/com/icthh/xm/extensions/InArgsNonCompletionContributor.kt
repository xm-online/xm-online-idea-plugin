package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.getLinkedConfigRootDir
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.isConfigProject
import com.icthh.xm.utils.getCountSubstring
import com.intellij.codeInsight.AnnotationUtil.*
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import com.intellij.psi.util.ClassUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import java.io.File
import java.util.*

class InArgsNonCompletionContributor: CompletionContributor() {

    init {

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                public override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    resultSet: CompletionResultSet
                ) {
                    val parent = parameters.position.parent
                    val inArgs = "lepContext.inArgs."
                    val lepName = parameters.position.originalFile.name.substringBefore("$$").replace("$", ".")

                    val project = parameters.position.project
                    if (parent != null && parent.text.startsWith(inArgs)) {

                        val psiManager = PsiManager.getInstance(project)
                        val annotationPsiClass = ClassUtil.findPsiClass(
                            psiManager,
                            LEP_ANNOTATION
                        ) ?: return

                        val annotatedPsiMethods = AnnotatedMembersSearch.search(annotationPsiClass).findAll()
                            .filterIsInstance<PsiMethod>()

                        val variants = annotatedPsiMethods.filter { isAnnotated(it, LEP_ANNOTATION, CHECK_TYPE) }
                            .filter { "\"${lepName}\"" == findAnnotation(it, LEP_ANNOTATION)?.findAttributeValue("value")?.text }
                            .flatMap { it.parameterList.parameters.toList() }
                            .map { it.name }
                        resultSet.addAllElements(variants.map { LookupElementBuilder.create(it) })
                    }
                }
            }
        )

    }

}
