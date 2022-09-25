package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.*
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.PsiReference.EMPTY_ARRAY
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import okio.Path.Companion.toPath
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.nio.file.Path


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

        registrar.registerProvider(entitySpecScalarField("ref", "definitions")) {element, _ ->
            createReference(element)
        }

        registrar.registerProvider(entitySpecScalarField("ref", "forms")) {element, _ ->
            createReference(element)
        }

        registrar.registerProvider(
            psiElement(JsonStringLiteral::class.java).withParent(
                psiElement(JsonProperty::class.java).withName("\$ref")
            )
        ) { element, _ ->
            refReferences(element)
        }
    }

    private fun refReferences(element: PsiElement): Array<PsiReference> {
        val formRefPrefix = "#/xmEntityForm/"
        val definitionRefPrefix = "#/xmEntityDefinition/"
        val value = (element.parent as JsonProperty).value
        val reference = value?.text?.trim('"') ?: ""
        if (reference.startsWith(definitionRefPrefix)) {
            val key = reference.substringAfter(definitionRefPrefix)
            val target = getAllDefinitionsKeyPsi(element.project, element.originalFile).find { it.valueText == key }
            if (target != null && value != null) {
                return arrayOf(toPsiReference(element, target))
            }
        }
        if (reference.startsWith(formRefPrefix)) {
            val key = reference.substringAfter(formRefPrefix)
            val target = getAllFormsKeyPsi(element.project, element.originalFile).find { it.valueText == key }
            if (target != null && value != null) {
                return arrayOf(toPsiReference(element, target))
            }
        }

        return emptyArray()
    }

    private fun createReference(element: PsiElement): Array<PsiReference> {
        val path = Path.of(element.getRootFolder() + "/" + element.firstChild.text.trim())
        val virtualFile = VfsUtil.findFile(path, false)
        if (virtualFile != null) {
            val psiFile = virtualFile.toPsiFile(element.project)
            if (psiFile != null) {
                return arrayOf(toPsiReference(element, psiFile))
            }
        }
        return emptyArray()
    }

    private fun referenceToState(element: PsiElement): Array<PsiReference> {
        val entityDefinition = element.getParentOfType<YAMLKeyValue>().getParentOfType<YAMLSequence>()
            .getParentOfType<YAMLSequenceItem>() ?: return emptyArray()
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
        return scalar.project.xmEntitySpecService.getEntitiesKeys(scalar.originalFile)
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
