package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiReference.EMPTY_ARRAY
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue


class XmEntitySpecReferenceContributor: PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLScalar::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    scalar: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (scalar !is YAMLScalar) return PsiReference.EMPTY_ARRAY

                    val element = scalar.firstChild
                    if (isSectionAttribute(element, "links", "typeKey")) {
                        return linkTypeKeyReferences(element, scalar)
                    }
                    if (isSectionAttribute(element, "functions", "key")) {
                        return functionFileReferences(element, scalar)
                    }
                    return PsiReference.EMPTY_ARRAY
                }
            })
    }

    private fun functionFileReferences(element: PsiElement, scalar: YAMLScalar): Array<PsiReference> {
        val functionFile = getFunctionFile(element) ?: return EMPTY_ARRAY
        val psiFile = functionFile.toPsiFile(element.project) ?: return EMPTY_ARRAY
        return Array(1) {
            toPsiReference(scalar, psiFile)
        }
    }

    private fun linkTypeKeyReferences(
        element: PsiElement,
        scalar: YAMLScalar
    ): Array<PsiReference> {
        val references = getAllEntityPsiElements(element)
            .map { it.value }.filterNotNull()
            .filter { element.text.trim().equals(it.text.trim()) }
            .map { toPsiReference(scalar, it) }
            .toTypedArray()

        return references
    }

    private fun toPsiReference(from: YAMLValue, to: PsiElement): PsiReference {
        return PsiReferenceImpl(from, to)
    }

}

class PsiReferenceImpl(from: YAMLValue, val to: PsiElement): PsiReferenceBase<YAMLValue>(from, true) {
    override fun resolve() = to
}
