package com.icthh.xm.extensions.entityspec

import com.icthh.xm.utils.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val IS_ENTITY_SPEC: Key<Boolean> = Key.create("IS_ENTITY_SPEC")
val ORIGINAL_FILE: Key<PsiFile> = Key.create("ORIGINAL_FILE")

fun <T> PsiElement.withCache(compute: () -> T): T {
    return CachedValuesManager.getManager(project).getCachedValue(this) {
        CachedValueProvider.Result.create(compute.invoke(), MODIFICATION_COUNT)
    }
}

class XmEntitySpecSchemaExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        val isFileExcluded =
            file.path.endsWith("/entity/xmentityspec.yml") || file.path.contains("/entity/xmentityspec/")
        return isFileExcluded
    }
}

fun getSchemaFile(): VirtualFile {
    return VfsUtil.findFileByURL(XmEntitySpecSchemaExclusion::class.java.classLoader.getResource("specs/entityspecschema.json"))!!
}

val psiFile = ConcurrentHashMap<PsiFile, Boolean>()
fun PsiFile?.isEntitySpecification(): Boolean {
    this ?: return false

    return psiFile.computeIfAbsent(this) {
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

fun PsiElement?.isEntitySpecification(): Boolean = this?.let {
    var isEntitySpec = it.getUserData(IS_ENTITY_SPEC)
    if (isEntitySpec == null) {
        isEntitySpec = originalFile.isEntitySpecification()
        it.putUserData(IS_ENTITY_SPEC, isEntitySpec)
    }
    return isEntitySpec
} ?: false

fun translateToLepConvention(xmEntitySpecKey: String): String {
    Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null")
    return xmEntitySpecKey.replace("-".toRegex(), "_").replace("\\.".toRegex(), "\\$")
}

private fun withEntityId(element: PsiElement): Boolean =
    element.getParentOfType<YAMLMapping>().getChildrenOfType<YAMLKeyValue>().filter {
        it.firstChild.text == "withEntityId"
    }.any {
        it.lastChild.text?.toBoolean() ?: false
    }

fun toFunctionKey(element: PsiElement): Pair<String, String> {
    val (functionKey, pathToFunction) = toLepKey(element.text)
    val functionPrefix = if (withEntityId(element)) {
        "FunctionWithXmEntity"
    } else {
        "Function"
    }
    val functionName = "${pathToFunction}${functionPrefix}${TWO_DOLLARS}${functionKey}${TWO_DOLLARS}tenant.groovy"
    return Pair(pathToFunction, functionName)
}

fun toLepKey(key: String): Pair<String, String> {
    var lepKey = translateToLepConvention(key.trim().trimStart('/'))
    var pathToLep = lepKey.substringBeforeLast('/', "")
    if (pathToLep.isNotBlank()) {
        pathToLep += '/'
    }
    lepKey = lepKey.substringAfterLast('/')
    return Pair(lepKey, pathToLep)
}

fun PsiDsl<out PsiElement>.entitySpec() {
    inFile {
        or({
            withFileType(YAMLFileType::class.java)
            withOriginalFile {
                withParentDirectoryName(PlatformPatterns.string().equalTo("xmentityspec"))
            }
        }, {
            withFileType(YAMLFileType::class.java)
            withOriginalFile {
                withParentDirectoryName(PlatformPatterns.string().equalTo("entity"))
            }
        })
    }
}

fun calendarEventFieldPlace(
    fieldName: String,
) = psiElement<PsiElement> {
    withPsiParent<YAMLScalar> {
        withPsiParent<YAMLKeyValue>(fieldName) {
            toKeyValue("events") {
                toKeyValue("calendars") {
                    toKeyValue("types")
                }
            }
        }
        entitySpec()
    }
}


fun allowedStateKeyPlace(
    innerPattern: PsiDsl<YAMLKeyValue>.() -> Unit = {}
) = psiElement<PsiElement> {
    withPsiParent<YAMLScalar> {
        toSequenceKeyValue("allowedStateKeys", innerPattern)
    }
}

fun allowedStateKeyScalarPlace(
    innerPattern: PsiDsl<YAMLKeyValue>.() -> Unit = {}
) = psiElement<YAMLScalar> {
    toSequenceKeyValue("allowedStateKeys", innerPattern)
}

fun calendarEventScalarFieldPlace(
    fieldName: String,
) = psiElement<YAMLScalar> {
    withPsiParent<YAMLKeyValue>(fieldName) {
        toKeyValue("events") {
            toKeyValue("calendars") {
                toKeyValue("types")
            }
        }
    }
    entitySpec()
}

fun entitySectionPlace(
    featureKey: String,
    attributeName: String
) = psiElement<PsiElement> {
    withPsiParent<YAMLScalar> {
        withPsiParent<YAMLKeyValue>(attributeName) {
            toKeyValue(featureKey) {
                toKeyValue("types")
            }
        }
    }
    entitySpec()
}

fun entityScalarSectionPlace(
    featureKey: String,
    attributeName: String
) = psiElement<YAMLScalar> {
    withPsiParent<YAMLKeyValue>(attributeName) {
        toKeyValue(featureKey) {
            toKeyValue("types")
        }
    }
    entitySpec()
}

fun entitySpecField(
    fieldName: String
) = psiElement<PsiElement> {
    withPsiParent<YAMLScalar> {
        withPsiParent<YAMLKeyValue>(fieldName) {
            toKeyValue("types")
        }
    }
}


fun getAllEntitiesKeys(project: Project,
                       originalFile: PsiFile): List<String> {
    val cachedValue = getEntitiesKeys(project, originalFile)
    return cachedValue.map { it.valueText }
}

fun getEntitiesKeys(
    project: Project,
    originalFile: PsiFile
): List<YAMLKeyValue> {
    val cachedValue = CachedValuesManager.getManager(project).getCachedValue(originalFile) {
        project.logger.info("\n\n\n UPDATE ${originalFile.name} cache \n\n\n")
        val keys = getEntityDeclarations(originalFile).getKeys()
        CachedValueProvider.Result.create(keys, MODIFICATION_COUNT)
    }
    return cachedValue ?: emptyList()
}

fun getEntityDeclarations(originalFile: PsiFile): YAMLSequence? = originalFile
    .getChildOfType<YAMLDocument>()
    .getChildOfType<YAMLMapping>()
    .getChildOfType<YAMLKeyValue>()
    .getChildOfType<YAMLSequence>()

fun getEntityKeys(element: PsiElement) = getEntitiesKeys(element.project, element.originalFile)

fun getAllEntityKeys(element: PsiElement) = getAllEntitiesKeys(element.project, element.originalFile)

fun getAllSubElements(
    element: PsiElement,
    sectionName: String,
    fieldName: String
): List<YAMLKeyValue> {
    return getEntityDeclarations(element.originalFile).mapToFields(sectionName)
        .map{ it.getChildOfType<YAMLSequence>().mapToFields(fieldName) }.flatten()
}

fun YAMLSequenceItem.getStateKeys(): List<String> {
    val stateKeys = stateKeysPsi().map { it.valueText }
    val result = ArrayList<String>()
    result.addAll(stateKeys)
    result.add("NEVER")
    return result
}

fun YAMLSequenceItem.stateKeysPsi(): List<YAMLKeyValue> {
    val states = this.keysValues.asSequence()
        .filter { it.keyTextMatches("states") }.map { it.value }.filterIsInstance<YAMLSequence>().toList()
    return states.map { it.getKeys() }.flatten()
}

fun nextStatePlace() = psiElement<PsiElement> {
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
}

val PsiElement.originalFile: PsiFile
    get() {
        var originalFile = getUserData(ORIGINAL_FILE)
        if (originalFile == null) {
            originalFile = containingFile.originalFile
            putUserData(ORIGINAL_FILE, originalFile)
        }
        return originalFile
    }
