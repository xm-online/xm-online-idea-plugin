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
            getAllEntitiesKeys(it).map { LookupElementBuilder.create(it) }
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

        JsonSchemaCompletionContributor.doCompletion(parameters, result, jsonSchemaObject)
    }

    private fun entityFeatureAttribute(
        featureKey: String,
        attributeName: String,
        elements: (parameters: CompletionParameters) -> List<LookupElementBuilder>
    ) {
        extendWithStop(BASIC, psiElement<PsiElement> {
            withPsiParent<YAMLScalar> {
                withPsiParent<YAMLKeyValue>(attributeName) {
                    toKeyValue(featureKey) {
                        toKeyValue("types")
                    }
                }
            }
            entitySpec()
        }, elements)
    }

    private fun nextStateKey() {
        extendWithStop(BASIC, psiElement<PsiElement> {
            withPsiParent<YAMLScalar> {
                withPsiParent<YAMLKeyValue>("stateKey") {
                    toKeyValue("next") {
                        toKeyValue("states") {
                            toKeyValue("types")
                        }
                    }
                }
            }
            entitySpec()
        }) {
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

private fun getAllEntitiesKeys(parameters: CompletionParameters): List<String> {


    val originalFile = parameters.originalFile
    val project = parameters.position.project

    val directory = originalFile.virtualFile.parent
    var xmentityspec: VirtualFile? = null
    if (directory.isDirectory && directory.name == "entity") {
        xmentityspec = directory.findChild("xmentityspec")
    } else if (directory.isDirectory && directory.name == "xmentityspec") {
        xmentityspec = directory
    }

    if (xmentityspec != null) {
        CachedValuesManager.getManager(project).getCachedValue(xmentityspec) {
            val keys = ArrayList<String>()
            val specs = VfsUtil.collectChildrenRecursively(xmentityspec)

            CachedValueProvider.Result.create(keys, EVER_CHANGED)
        }
    }

    val cachedValue = getEntitiesKeys(project, originalFile)
    return cachedValue
}

private fun getEntitiesKeys(
    project: Project,
    originalFile: PsiFile
): List<String> {
    val cachedValue = CachedValuesManager.getManager(project).getCachedValue(originalFile) {
        project.logger.info("\n\n\n UPDATE ${originalFile.name} cache \n\n\n")
        val keys = originalFile
            .getChildOfType<YAMLDocument>()
            .getChildOfType<YAMLMapping>()
            .getChildOfType<YAMLKeyValue>()
            .getChildOfType<YAMLSequence>().getKeys().map { it.valueText }
        // TODO write own tracker
        CachedValueProvider.Result.create(keys, MODIFICATION_COUNT)
    }
    return cachedValue ?: emptyList()
}

@Deprecated("")
fun PsiElement.isChildConfigAttribute(attributeName: String): Boolean {
    val attributeKeyValuePair = goSuperParent()
    return attributeKeyValuePair is YAMLKeyValueImpl && attributeKeyValuePair.keyText.equals(attributeName)
}

fun getSchemaFile(): VirtualFile {
    return VfsUtil.findFileByURL(XmEntitySpecCompletionContributor::class.java.classLoader.getResource("specs/entityspecschema.json"))!!
}

@Deprecated("")
fun PsiElement?.goSuperParent() = this?.parent?.parent

@Deprecated("")
fun List<PsiElement>.goChild() = flatMap { it.children.toList() }

@Deprecated("")
fun List<PsiElement>.goSubChild() = this.goChild().goChild()

@Deprecated("")
fun PsiElement.goChild() = this.children.toList()

@Deprecated("")
fun PsiElement.goSubChild() = this.goChild().goChild()

@Deprecated("")
fun isUnderEntityConfig(position: PsiElement): Boolean {
    val types = getConfigDefinition(position).goSuperParent().goSuperParent()
    return types is YAMLKeyValueImpl && types.keyText.equals("types")
}

@Deprecated("")
fun isConfigSection(position: PsiElement, sectionName: String): Boolean {
    val linksDefinition = getConfigDefinition(position)
    return linksDefinition is YAMLKeyValueImpl && linksDefinition.keyText.equals(sectionName)
}

@Deprecated("")
fun getConfigDefinition(position: PsiElement) =
    position.goParentSection()?.parent

@Deprecated("")
fun PsiElement?.goParentSection() = goSuperParent().goSuperParent()?.parent

@Deprecated("")
fun PsiElement?.goSuperParentSection() = goParentSection().goParentSection()

@Deprecated("")
fun isNextStateKey(element: PsiElement): Boolean {
    val states = element.goSuperParentSection()
    val isStates = states is YAMLKeyValue && states.keyText.equals("states")
    val stateKey = element.goSuperParent()
    val isStateKey = stateKey is YAMLKeyValue && stateKey.keyText.equals("stateKey")
    val next = element.goSuperParent().goSuperParent()?.goSuperParent()
    val isNext = next is YAMLKeyValue && next.keyText.equals("next")
    return isStates && isStateKey && isNext;
}

fun PsiElement?.isEntitySpecification() = this?.containingFile?.originalFile?.isEntitySpecification() ?: false

private val isInSpec = ConcurrentHashMap<PsiFile?, Boolean>()
fun PsiFile?.isEntitySpecification(): Boolean {
    this ?: return false
    return isInSpec.computeIfAbsent(this) {

        val isEntitySpecDir = containingDirectory?.name?.equals("xmentityspec") ?: false
        val isYamlFile = name.endsWith(".yml")
        if (isEntitySpecDir && isYamlFile) {
            return@computeIfAbsent true
        }
        val isEntityDir = this.containingDirectory?.name?.equals("entity") ?: return@computeIfAbsent false
        val isEntitySpecFile = "xmentityspec.yml".equals(name)
        return@computeIfAbsent isEntityDir && isEntitySpecFile
    }
}

fun PsiDsl<out PsiElement>.entitySpec() {
    inFile {
        or({
            withFileType(YAMLFileType::class.java)
            withOriginalFile {
                withParentDirectoryName(string().equalTo("xmentityspec"))
            }
        }, {
            withFileType(YAMLFileType::class.java)
            withOriginalFile {
                withParentDirectoryName(string().equalTo("entity"))
            }
        })
    }
}

class XmEntitySpecSchemaExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        val isFileExcluded =
            file.path.endsWith("/entity/xmentityspec.yml") || file.path.contains("/entity/xmentityspec/")
        return isFileExcluded
    }
}
