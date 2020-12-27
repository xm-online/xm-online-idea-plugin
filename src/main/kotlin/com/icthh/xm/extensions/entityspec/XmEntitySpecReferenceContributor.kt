package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.*
import com.intellij.psi.*
import com.intellij.psi.PsiReference.EMPTY_ARRAY
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem


class XmEntitySpecReferenceContributor: PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerProvider(entityScalarSectionPlace("links", "typeKey" )) {element, _ ->
            element.withCache {
                entityTypeKeyReferences(element)
            }
        }
        registrar.registerProvider(entityScalarSectionPlace("functions", "key")) {element, _ ->
            element.withCache {
                functionFileReferences(element)
            }
        }
        registrar.registerProvider(nextStatePsiPattern()) {element, _ ->
            element.withCache {
                statesReferences(element)
            }
        }
        registrar.registerProvider(allowedStateKeyScalarPlace()) {element, _ ->
            element.withCache {
                referenceToState(element)
            }
        }
        registrar.registerProvider(calendarEventScalarFieldPlace("dataTypeKey")) {element, _ ->
            element.withCache {
                entityTypeKeyReferences(element)
            }
        }
    }

    private fun referenceToState(element: PsiElement): Array<PsiReference> {
        val entityDefinition = element.getParentOfType<YAMLKeyValue>().getParentOfType<YAMLSequence>()
            .getParentOfType<YAMLSequenceItem>()!!
        return entityDefinition.stateKeysPsi().filter { element.text.trim() == it.valueText.trim() }
            .map { toPsiReference(element, it) }
            .toTypedArray()
    }

    private fun statesReferences(element: PsiElement): Array<PsiReference> {
        val states = element.findFirstParent {
            it is YAMLKeyValue && it.keyTextMatches("states")
        }
        return states.getChildOfType<YAMLSequence>().getKeys().filter { element.text.trim() == it.valueText.trim() }
            .map { toPsiReference(element, it) }
            .toTypedArray()
    }

    private fun nextStatePsiPattern() =
        psiElement<YAMLScalar> {
            withPsiParent<YAMLKeyValue>("stateKey") {
                toKeyValue("next") {
                    toKeyValue("states") {
                        toKeyValue("types")
                    }
                }
            }
        }


    private fun functionFileReferences(element: PsiElement): Array<PsiReference> {
        val value = element.firstChild
        val functionFile = getFunctionFile(value) ?: return EMPTY_ARRAY
        val psiFile = functionFile.toPsiFile(value.project) ?: return EMPTY_ARRAY
        return Array(1) {
            toPsiReference(element, psiFile)
        }
    }

    private fun entityTypeKeyReferences(
        scalar: PsiElement
    ): Array<PsiReference> {
        val element = scalar.firstChild
        return getEntitiesKeys(scalar.project, scalar.originalFile)
            .mapNotNull { it.value }
            .filter { element.text.trim() == it.text.trim() }
            .map { toPsiReference(scalar, it) }
            .toTypedArray()
    }

    private fun toPsiReference(from: PsiElement, to: PsiElement): PsiReference {
        return PsiReferenceImpl(from, to)
    }

}

class PsiReferenceImpl(from: PsiElement, val to: PsiElement): PsiReferenceBase<PsiElement>(from, true) {
    override fun resolve() = to
}
