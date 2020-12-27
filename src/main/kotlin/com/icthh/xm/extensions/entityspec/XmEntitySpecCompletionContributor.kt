package com.icthh.xm.extensions.entityspec

import com.icthh.xm.extensions.entityspec.IconProvider.iconsSet
import com.icthh.xm.utils.*
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType.BASIC
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker.EVER_CHANGED
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.string
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class XmEntitySpecCompletionContributor() : CompletionContributor() {

    val jsonSchema = AtomicReference<JsonSchemaObject>()

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

    init {

        extendWithStop(BASIC, psiElement<PsiElement> {
            withPsiParent<YAMLScalar> {
                withPsiParent<YAMLKeyValue>("icon")
            }
            entitySpec()
        }) { icons(it) }

        entityFeatureAttribute("links", "typeKey") { it: CompletionParameters ->
            getAllEntitiesKeys(it.position.project, it.originalFile).map { LookupElementBuilder.create(it) }
        }

        extendWithStop(BASIC, calendarEventFieldPlace("dataTypeKey")) {
            getAllEntitiesKeys(it.position.project, it.originalFile).map { LookupElementBuilder.create(it) }
        }

        entityFeatureAttribute("links", "builderType") { it: CompletionParameters ->
            listOf("NEW", "SEARCH").map { LookupElementBuilder.create(it) }
        }

        nextStateKey()
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
