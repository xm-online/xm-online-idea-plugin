package com.icthh.xm.extensions

import com.icthh.xm.utils.logger
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl

class XmEntitySpecCompletionContributor : CompletionContributor() {

    init {
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {

        val position = parameters.position
        val file = JsonCachedValues.getSchemaObject(getSchemaFile(), position.project)
        if (file != null) {
            JsonSchemaCompletionContributor.doCompletion(parameters, result, file)
        }

        if (isLinkTypeKey(parameters)) {
            getAllEntitiesKeys(parameters).forEach {
                result.addElement(LookupElementBuilder.create(it))
            }
        }

    }

    private fun isLinkTypeKey(parameters: CompletionParameters): Boolean {
        if (parameters.isChildConfigAttribute("typeKey")) {
            val linksDefinition = parameters.position.goSuperParent().goSuperParent().goSuperParent().goSuperParent().goSuperParent()?.parent
            if (linksDefinition is YAMLKeyValueImpl && linksDefinition.keyText.equals("links")) {
                val types = linksDefinition.goSuperParent().goSuperParent().goSuperParent().goSuperParent()
                return types is YAMLKeyValueImpl && types.keyText.equals("types")
            }
            return false
        } else {
            return false
        }
    }

    private fun CompletionParameters.isChildConfigAttribute(attributeName: String): Boolean {
        val attributeKeyValuePair = position.goSuperParent().goSuperParent()
        return attributeKeyValuePair is YAMLKeyValueImpl && attributeKeyValuePair.keyText.equals(attributeName)
    }

    private fun getAllEntitiesKeys(parameters: CompletionParameters) =
        parameters.originalFile.children.filter { it is YAMLDocument }.flatMap {
            it.goSubChild().goSubChild().goSubChild()
                .filter { it is YAMLKeyValue && it.keyText.equals("key") }
                .filter { it.children.size == 1 }
                .filter { it.children[0] is YAMLPlainTextImpl }
                .map { it.children[0] as YAMLPlainTextImpl }
                .map {it.textValue}
        }


    fun PsiElement?.goSuperParent() = this?.parent

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

    private fun getSchemaFile(): VirtualFile {
        return VfsUtil.findFileByURL(javaClass.classLoader.getResource("specs/entityspecschema.json"))!!
    }
}

class XmEntitySpecSchemaExclusion: JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        val isFileExcluded = file.path.endsWith("/entity/xmentityspec.yml")
        logger.info("isFileExcluded > ${isFileExcluded}")
        return isFileExcluded
    }
}