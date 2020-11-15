package com.icthh.xm.extensions.entityspec

import com.icthh.xm.extensions.entityspec.IconProvider.iconsSet
import com.icthh.xm.utils.logger
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl
import java.util.concurrent.ConcurrentHashMap

class XmEntitySpecCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {

        if (parameters.position !is LeafPsiElement || !parameters.position.isEntitySpecification()) {
            return
        }

        val position = parameters.position
        val file = JsonCachedValues.getSchemaObject(getSchemaFile(), position.project)
        if (file != null) {
            JsonSchemaCompletionContributor.doCompletion(parameters, result, file)
        }

        if (isLinkAttribute(parameters, "typeKey")) {
            getAllEntitiesKeys(parameters).forEach {
                result.addElement(LookupElementBuilder.create(it))
            }
            return
        }

        if (isLinkAttribute(parameters, "builderType")) {
            result.addElement(LookupElementBuilder.create("NEW"))
            result.addElement(LookupElementBuilder.create("SEARCH"))
            return
        }

        if (isNextStateKey(parameters.position)) {
            val states = parameters.position.goSuperParentSection()
            getAllStateKeys(states).forEach {
                result.addElement(LookupElementBuilder.create(it))
            }
            return
        }

        val element = parameters.position.parent?.parent
        if (element is YAMLKeyValue && element.keyText.equals("icon")) {
            val editor = FileEditorManager.getInstance(position.project).selectedEditor as? TextEditor ?: return
            val fontSize = editor.editor.colorsScheme.editorFontSize

            iconsSet.forEach {
                it.value.updateFontSize(fontSize)
                result.addElement(LookupElementBuilder.create(it.key).withIcon(it.value))
            }
        }

    }

    private fun isLinkAttribute(parameters: CompletionParameters, attrName: String): Boolean {
        return isSectionAttribute(parameters, "links", attrName)
    }


    private fun isSectionAttribute(
        parameters: CompletionParameters,
        section: String,
        attrName: String
    ): Boolean {
        return (parameters.position.isChildConfigAttribute(attrName)
                && isConfigSection(parameters.position, section)
                && isUnderEntityConfig(parameters.position))
    }

    private fun getAllEntitiesKeys(parameters: CompletionParameters) =
        parameters.originalFile.children.filter { it is YAMLDocument }.flatMap {
            it.goSubChild().goSubChild().goSubChild()
                .filter { it is YAMLKeyValue && it.keyText.equals("key") }
                .filter { it.children.size == 1 }
                .filter { it.children[0] is YAMLPlainTextImpl }
                .map { it.children[0] as YAMLPlainTextImpl }
                .map { it.textValue }
        }

    private fun getAllStateKeys(element: PsiElement?): List<String> {
        element ?: return emptyList()
        return element.goSubChild().goSubChild()
            .filter { it is YAMLKeyValue && it.keyText.equals("key") }
            .filter { it.children.size == 1 }
            .filter { it.children[0] is YAMLPlainTextImpl }
            .map { it.children[0] as YAMLPlainTextImpl }
            .map { it.textValue }
    }
}

fun PsiElement.isChildConfigAttribute(attributeName: String): Boolean {
    val attributeKeyValuePair = goSuperParent()
    return attributeKeyValuePair is YAMLKeyValueImpl && attributeKeyValuePair.keyText.equals(attributeName)
}

fun getSchemaFile(): VirtualFile {
    return VfsUtil.findFileByURL(XmEntitySpecCompletionContributor::class.java.classLoader.getResource("specs/entityspecschema.json"))!!
}

fun PsiElement?.goSuperParent() = this?.parent?.parent

fun List<PsiElement>.goChild() = flatMap { it.children.toList() }

fun List<PsiElement>.goSubChild() = this.goChild().goChild()

fun PsiElement.goChild() = this.children.toList()

fun PsiElement.goSubChild() = this.goChild().goChild()

fun PsiElement.bySubChild(apply: (PsiElement) -> Unit) {
    children.forEach {
        it.children.forEach {
            apply.invoke(it)
        }
    }
}

fun isUnderEntityConfig(position: PsiElement): Boolean {
    val types = getConfigDefinition(position).goSuperParent().goSuperParent()
    return types is YAMLKeyValueImpl && types.keyText.equals("types")
}

fun isConfigSection(position: PsiElement, sectionName: String): Boolean {
    val linksDefinition = getConfigDefinition(position)
    return linksDefinition is YAMLKeyValueImpl && linksDefinition.keyText.equals(sectionName)
}

fun getConfigDefinition(position: PsiElement) =
    position.goParentSection()?.parent

fun PsiElement?.goParentSection() = goSuperParent().goSuperParent()?.parent

fun PsiElement?.goSuperParentSection() = goParentSection().goParentSection()

fun isNextStateKey(element: PsiElement): Boolean {
    val states = element.goSuperParentSection()
    val isStates = states is YAMLKeyValue && states.keyText.equals("states")
    val stateKey = element.goSuperParent()
    val isStateKey = stateKey is YAMLKeyValue && stateKey.keyText.equals("stateKey")
    val next = element.goSuperParent().goSuperParent()?.goSuperParent()
    val isNext = next is YAMLKeyValue && next.keyText.equals("next")
    return isStates && isStateKey && isNext;
}

private val isInSpec = ConcurrentHashMap<PsiElement?, Boolean>()
fun PsiElement?.isEntitySpecification(): Boolean {
    return isInSpec.computeIfAbsent(this) {
        val isEntityDir = this?.containingFile?.originalFile?.containingDirectory?.virtualFile?.path?.endsWith("/entity") ?: return@computeIfAbsent false
        val isEntitySpecFile = "xmentityspec.yml".equals(this.containingFile?.originalFile?.name)
        return@computeIfAbsent isEntityDir && isEntitySpecFile
    }
}

class XmEntitySpecSchemaExclusion: JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        val isFileExcluded = file.path.endsWith("/entity/xmentityspec.yml")
        return isFileExcluded
    }
}
