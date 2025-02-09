package com.icthh.xm.xmeplugin.yaml.exts

import com.icthh.xm.xmeplugin.utils.PsiReferenceImpl
import com.icthh.xm.xmeplugin.utils.log
import com.icthh.xm.xmeplugin.utils.psiElement
import com.icthh.xm.xmeplugin.utils.toPsiFile
import com.icthh.xm.xmeplugin.yaml.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.*
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import getTenantFolder
import getTenantName
import macthPattern
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import runMessageTemplate
import toVirtualFile


class YamlReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement<PsiElement>()
            .inFile(filePattern())
            .with(elementPattern()), yamlReferenceProvider())
    }

    private fun filePattern() = psiFile().with(object : PatternCondition<PsiFile>("xme.file.specs") {
        override fun accepts(t: PsiFile, context: ProcessingContext?): Boolean {
            return t.project.xmePluginSpecService.getSpecifications(t).isNotEmpty()
        }
    })

    private fun elementPattern() = object: PatternCondition<PsiElement>("xme.yaml.pattern") {
        override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
            val project = element.project
            return project.xmePluginSpecService.getSpecifications(element.containingFile).any { spec ->
                spec.references.any { ref ->
                    matchElementPath(element, ref)
                }
            }
        }
    }

    private fun matchElementPath(element: PsiElement, it: ReferenceEntry): Boolean {
        if (element is YAMLKeyValue) {
            val value = element.value ?: return false
            return macthPattern(value, it.elementPath)
        } else {
            return macthPattern(element, it.elementPath)
        }
    }


    private fun yamlReferenceProvider() = object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
            if (element !is YAMLScalar) return PsiReference.EMPTY_ARRAY

            val project = element.project
            val references = mutableListOf<PsiReference>()
            project.xmePluginSpecService.getSpecifications(element.containingFile).forEach { spec ->
                spec.references.filter {
                    matchElementPath(element, it)
                }.forEach { ref ->
                    try {
                        val nodeContext = project.xmePluginSpecMetaInfoService.getYamlContext(element, spec.key)
                        nodeContext?.let { computeReferences(spec, ref, references, element, element, nodeContext) }
                    } catch (e: Exception) {
                        if (e is ControlFlowException) {
                            throw e
                        }
                        element.log.error("Error in computeReferences: ${e.message}", e)
                    }
                }
            }

            if (references.isEmpty()) {
                return PsiReference.EMPTY_ARRAY
            }
            return references.toTypedArray()
        }

        private fun computeReferences(
            spec: Specification,
            ref: ReferenceEntry,
            references: MutableList<PsiReference>,
            refSource: PsiElement,
            element: PsiElement,
            nodeContext: YamlContext
        ) {
            val tenantName = element.containingFile.getTenantName()
            if (ref.reference?.type == "file") {
                val tenantFolder = element.containingFile.getTenantFolder()
                val filePathTemplates = ref.reference?.filePathTemplates ?: emptyList()
                val fileRefs = filePathTemplates.mapNotNull {
                    runMessageTemplate(nodeContext, it, ref.includeFunctions, element.project)
                }.map {
                    it.trimStart('/')
                }.mapNotNull {
                    "$tenantFolder/$it".toVirtualFile()
                }.mapNotNull {
                    it.toPsiFile(element.project)
                }.map {
                    PsiReferenceImpl(refSource, it)
                }

                if (ref.reference?.required == true && fileRefs.isEmpty()) {
                    references.add(PsiReferenceImpl(refSource, null))
                } else {
                    references.addAll(fileRefs)
                }
            }
            if (ref.reference?.type == "element") {
                val elementPathTemplates = ref.reference?.elementPathTemplates ?: emptyList()
                val elementRefs = elementPathTemplates.mapNotNull {
                    runMessageTemplate(nodeContext, it, ref.includeFunctions, element.project)
                }.map {
                    element.project.xmePluginSpecService.parsePattern(it)
                }.flatMap {
                    element.project.xmePluginSpecService.getFiles(tenantName, spec.key).flatMap { file ->
                        findElement(file, it) // TODO transform dsl to path expression [now operation too slow]
                    }
                }.map {
                    PsiReferenceImpl(refSource, it)
                }

                if (ref.reference?.required == true && elementRefs.isEmpty()) {
                    references.add(PsiReferenceImpl(refSource, null))
                } else {
                    references.addAll(elementRefs)
                }
            }
        }
    }

}
