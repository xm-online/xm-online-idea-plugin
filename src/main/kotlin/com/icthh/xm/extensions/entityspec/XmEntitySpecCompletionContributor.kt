package com.icthh.xm.extensions.entityspec

import com.icthh.xm.extensions.entityspec.IconProvider.iconsSet
import com.icthh.xm.utils.*
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType.BASIC
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonReferenceExpression
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.contextOfType
import com.intellij.psi.util.parentOfType
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.streams.toList

class XmEntitySpecCompletionContributor() : CompletionContributor() {

    val jsonSchema = AtomicReference<JsonSchemaObject>()
    init {

        extendWithStop(BASIC,
            psiElement(PsiElement::class.java).withParent(
                psiElement(JsonStringLiteral::class.java).withParent(
                    psiElement(JsonProperty::class.java).withName("\$ref")
                )
            )
        ) {
            refVariants(it).map { LookupElementBuilder.create(it) }
        }

        extendWithStop(BASIC,
            psiElement(PsiElement::class.java).withParent(
                psiElement(JsonReferenceExpression::class.java).withParent(
                    psiElement(JsonProperty::class.java).withName("\$ref")
                )
            )
        ) {
            refVariants(it).map { "\"${it}\"" }.map { LookupElementBuilder.create(it) }
        }

        extendWithStop(BASIC, entitySpecField("ref", "definitions")) {
            fileVariants(it, "definitions")
        }

        extendWithStop(BASIC, entitySpecField("ref", "forms")) {
            fileVariants(it, "forms")
        }

        extendWithStop(BASIC, psiElement<PsiElement> {
            withPsiParent<YAMLScalar> {
                withPsiParent<YAMLKeyValue>("icon")
            }
            entitySpec()
        }) { icons(it) }

        entityFeatureAttribute("links", "typeKey") {
            getAllEntitiesKeys(it.position.project, it.originalFile).map { LookupElementBuilder.create(it) }
        }

        extendWithStop(BASIC, calendarEventFieldPlace("dataTypeKey")) {
            getAllEntitiesKeys(it.position.project, it.originalFile).map { LookupElementBuilder.create(it) }
        }

        extendWithStop(BASIC, allowedStateKeyPlace()) {
            val entityDefinition = it.position.getParentOfType<YAMLKeyValue>().getParentOfType<YAMLSequence>().getParentOfType<YAMLSequenceItem>()
            entityDefinition ?: return@extendWithStop emptyList()
            val result = entityDefinition.getStateKeys()
            result.map { LookupElementBuilder.create(it) }
        }

        entityFeatureAttribute("links", "builderType") {
            listOf("NEW", "SEARCH").map { LookupElementBuilder.create(it) }
        }

        nextStateKey()
    }

    private fun refVariants(it: CompletionParameters): List<String> {
        val datas = setOf("dataSpec", "inputSpec", "contextDataSpec")
        val forms = setOf("dataForm", "inputForm", "contextDataForm")
        val fieldName = it.position.contextOfType(YAMLKeyValue::class)
        val block = fieldName?.parentOfType<YAMLKeyValue>()
        if (forms.contains(fieldName?.keyText) || block.keyTextMatches("forms")) {
            return getAllFormsKeys(it.position).map { "#/xmEntityForm/${it}" }
        }
        if (datas.contains(fieldName?.keyText) || block.keyTextMatches("definitions")) {
            return getAllDefinitionsKeys(it.position).map { "#/xmEntityDefinition/${it}" }
        }
        return listOf()
    }

    private fun fileVariants(it: CompletionParameters, folder: String): List<LookupElementBuilder> {
        val project = it.position.project
        val tenantName = it.originalFile.getTenantName(project)
        val path = "/config/tenants/${tenantName}/entity/xmentityspec/$folder"
        val variants = Files.walk(Paths.get("${project.basePath}/$path")).filter(Files::isRegularFile).map {
            "xmentityspec/$folder${it.absolutePathString().substringAfter(path)}"
        }.filter { it.endsWith(".json") }.toList()
        return variants.map { LookupElementBuilder.create(it) }
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        start("fillCompletionVariants")
        doWork(parameters, result)
        stop("fillCompletionVariants")
    }

    private fun doWork(
        parameters: CompletionParameters,
        result: CompletionResultSet
    ) {
        if (parameters.position !is LeafPsiElement || !parameters.originalFile.isEntitySpecification()) {
            return
        }
        jsonSchemaCompletion(parameters, result)
        super.fillCompletionVariants(parameters, result)
    }

    private fun jsonSchemaCompletion(
        parameters: CompletionParameters,
        result: CompletionResultSet
    ) {

        var jsonSchemaObject = jsonSchema.get()
        if (jsonSchemaObject == null) {
            val position = parameters.position
            val file = JsonCachedValues.getSchemaObject(getSchemaFile(), position.project)
            jsonSchema.set(file)
            jsonSchemaObject = file
        }

        JsonSchemaCompletionContributor.doCompletion(parameters, result, jsonSchemaObject, true)
    }

    private fun entityFeatureAttribute(
        featureKey: String,
        attributeName: String,
        elements: (parameters: CompletionParameters) -> List<LookupElementBuilder>
    ) {
        extendWithStop(BASIC, entitySectionPlace(featureKey, attributeName), elements)
    }

    private fun nextStateKey() {
        extendWithStop(BASIC, nextStatePlace()) {
            val states = it.position.findFirstParent {
                it is YAMLKeyValue && it.keyTextMatches("states")
            }
            val values = states.getChildOfType<YAMLSequence>().getKeys()
                .map { LookupElementBuilder.create(it.valueText) }
            values
        }
    }

    private fun icons(
        parameters: CompletionParameters
    ): List<LookupElementBuilder> {
        val position = parameters.position
        val editor = FileEditorManager.getInstance(position.project).selectedEditor as? TextEditor ?: return emptyList()
        val fontSize = editor.editor.colorsScheme.editorFontSize
        iconsSet.forEach {
            it.value.updateFontSize(fontSize)
        }
        return iconsSet.map { LookupElementBuilder.create(it.key).withIcon(it.value) }.toList()
    }

}
