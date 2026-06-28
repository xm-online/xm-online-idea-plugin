package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.utils.PsiReferenceImpl
import com.icthh.xm.xmeplugin.utils.containerFile
import com.icthh.xm.xmeplugin.utils.originalFile
import com.icthh.xm.xmeplugin.utils.registerProvider
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.jetbrains.yaml.psi.YAMLKeyValue

private const val DEFINITION_REF_PREFIX = "#/xmEntityDefinition/"
private const val FORM_REF_PREFIX = "#/xmEntityForm/"

/**
 * Restores Cmd+Click navigation from a JSON Schema `"$ref": "#/xmEntityDefinition/<key>"` (or
 * `"#/xmEntityForm/<key>"`) inside an injected xmentityspec dataSpec/value JSON block to the matching
 * `definitions[].key` / `forms[].key` declaration in the tenant's xmentityspec files.
 *
 * This existed before the Feb-2025 package rewrite (old extensions/entityspec/XmEntitySpecReferenceContributor)
 * and was dropped when entity-spec support moved to the generic YAML-DSL spec system, which cannot address
 * elements inside injected JSON. Registered for language="JSON" in plugin.xml.
 */
class XmEntitySpecRefReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerProvider(
            psiElement(JsonStringLiteral::class.java).withParent(
                psiElement(JsonProperty::class.java).withName("\$ref")
            )
        ) { element, _ ->
            refReferences(element)
        }
    }

    private fun refReferences(element: PsiElement): Array<PsiReference> {
        val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY

        // The $ref lives in JSON injected into a YAML scalar; map back to the host YAML file and only
        // resolve when it is an xmentityspec file (skip unrelated JSON files that also use such $refs).
        val hostFile = element.originalFile.containerFile()
        if (!hostFile.virtualFile.isEntitySpecification()) {
            return PsiReference.EMPTY_ARRAY
        }

        val reference = literal.value
        val (targetKey, keysPsi) = when {
            reference.startsWith(DEFINITION_REF_PREFIX) ->
                reference.substringAfter(DEFINITION_REF_PREFIX) to hostFile.getDefinitionKeysPsi()
            reference.startsWith(FORM_REF_PREFIX) ->
                reference.substringAfter(FORM_REF_PREFIX) to hostFile.getFormKeysPsi()
            else -> return PsiReference.EMPTY_ARRAY
        }

        val target: YAMLKeyValue = keysPsi.firstOrNull { it.valueText == targetKey }
            ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(PsiReferenceImpl(element, target))
    }
}
