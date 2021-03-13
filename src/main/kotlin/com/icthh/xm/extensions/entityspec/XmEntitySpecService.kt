package com.icthh.xm.extensions.entityspec

import com.icthh.xm.extensions.entityspec.XmEntitySpecInfo.Companion.NULL_OBJECT
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.toPsiFile
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
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.KProperty1

val IS_ENTITY_SPEC: Key<Boolean> = Key.create("IS_ENTITY_SPEC")
val ORIGINAL_FILE: Key<PsiFile> = Key.create("ORIGINAL_FILE")
val TENANT_NAME: Key<String> = Key.create("TENANT_NAME")
val XMENTITY_SPEC_SERVICE: Key<XmEntitySpecService> = Key.create("XMENTITY_SPEC_SERVICE")

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
    return project.xmEntitySpecService.getEntitiesKeys(originalFile).map { it.valueText }
}

fun getEntityKeys(element: PsiElement) = element.project.xmEntitySpecService.getEntitiesKeys(element.originalFile)

fun getAllEntityKeys(element: PsiElement) = getAllEntitiesKeys(element.project, element.originalFile)

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

fun PsiFile.getTenantName(project: Project): String {
    var tenantName = getUserData(TENANT_NAME)
    if (tenantName == null) {
        tenantName = virtualFile.getTenantName(project)
        putUserData(TENANT_NAME, tenantName)
    }
    return tenantName
}

val Project.xmEntitySpecService: XmEntitySpecService
    get() {
        val service = this.getUserData(XMENTITY_SPEC_SERVICE)
        if (service == null) {
            return synchronized(this) {
                var userData = getUserData(XMENTITY_SPEC_SERVICE)
                if (userData == null) {
                    userData = XmEntitySpecService(this)
                    putUserData(XMENTITY_SPEC_SERVICE, userData)
                }
                userData
            }
        }
        return service
    }

class XmEntitySpecService(val project: Project) {
    private val entityByTenants: MutableMap<String, XmEntitySpecInfo> = ConcurrentHashMap()
    private val filesByTenants: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
    private val entityFiles: MutableMap<String, XmEntitySpecInfo> = ConcurrentHashMap()

    fun getByTenant(tenant: String) = entityByTenants.computeIfAbsent(tenant) {
        computeTenantEntityInfo(tenant)
    }

    fun getEntitiesKeys(
        originalFile: PsiFile
    ): List<YAMLKeyValue> {
        val result = getEntitySpec(originalFile)
        return result.keys
    }

    fun getEntitySpec(originalFile: PsiFile): XmEntitySpecInfo {
        start("getEntitiesSpec")
        val tenant = originalFile.getTenantName(project)
        val result = entityByTenants.computeIfAbsent(tenant) {
            computeTenantEntityInfo(tenant)
        }
        stop("getEntitiesSpec")
        return result
    }

    fun removeEntityFile(file: VirtualFile) {
        logger.info("removeEntityFile")
        val tenantName = file.getTenantName(project)
        val files = filesByTenants.get(tenantName)
        val path = file.path.substring(project.basePath?.length ?: 0)
        files?.remove(path)
        entityFiles.remove(path)
        entityByTenants.remove(tenantName)
    }

    fun updateEntityFile(file: VirtualFile) {
        logger.info("updateEntityFile")
        val tenantName = file.getTenantName(project)
        val files = filesByTenants.get(tenantName)
        val path = file.path.substring(project.basePath?.length ?: 0)
        files?.add(path)
        entityFiles.put(path, file.toXmEntitySpecInfo())
        entityByTenants.remove(tenantName)
    }

    fun computeTenantEntityInfo(tenantName: String): XmEntitySpecInfo {
        logger.info("computeTenantEntityInfo")
        val builder = XmEntitySpecInfoBuilder(tenantName)
        filesByTenants.computeIfAbsent(tenantName) {
            computeEntityFiles(tenantName)
        }.map{
            entityFiles.computeIfAbsent(it) {
                computeEntityInfo(it)
            }
        }.forEach {
            builder.add(it)
        }
        return builder.toXmEntitySpecInfo()
    }

    private fun computeEntityFiles(
        tenantName: String
    ): HashSet<String> {
        logger.info("computeEntityFiles")
        val files = HashSet<String>()
        val path = "/config/tenants/${tenantName}/entity/xmentityspec"
        val specYml = VfsUtil.findFile(File("${project.basePath}/$path.yml").toPath(), true)
        val psiFile = specYml?.toPsiFile(project)
        psiFile?.let { files.add(path + ".yml") }

        val specDirectory = VfsUtil.findFile(File("${project.basePath}/$path").toPath(), true)
        specDirectory?.children?.toList()?.mapNotNull { it.toPsiFile(project) }?.forEach {
            files.add(path + "/" + it.name)
        }
        return files
    }

    private fun VirtualFile.toXmEntitySpecInfo(): XmEntitySpecInfo {
        val psiFile = toPsiFile(project) ?: return NULL_OBJECT
        return psiFile.toXmEntitySpecInfo()
    }

    private fun PsiFile.toXmEntitySpecInfo(): XmEntitySpecInfo {
        return XmEntitySpecInfo(
            getTenantName(this.project),
            getFileXmEntityKeys(),
            getAllKeys("functions", "key"),
            getAllFunctionKeysWithEntityId(),
            getAllEventsKeys(),
            getAllKeys("links", "key"),
            getAllKeys("attachments", "key"),
            getAllKeys("calendars", "key"),
            getAllKeys("locations", "key"),
            getAllKeys("tags", "key"),
            getAllKeys("comments", "key"),
            getAllKeys("ratings", "key")
        )
    }

    private fun PsiFile.getFileXmEntityKeys(): List<YAMLKeyValue> {
        val file = originalFile
        file.virtualFile.refresh(false, false)
        project.logger.info("\n\n\n UPDATE ${file.name} cache \n\n\n")
        return file.getEntityDeclarations().getKeys()
    }

    private fun computeEntityInfo(
        path: String
    ): XmEntitySpecInfo {
        logger.info("computeEntityInfo")
        val specYml = VfsUtil.findFile(File("${project.basePath}$path").toPath(), true)
        val psiFile = specYml?.toPsiFile(project)
        return psiFile?.toXmEntitySpecInfo() ?: NULL_OBJECT
    }

    private fun PsiFile.getEntityDeclarations(): YAMLSequence? = this
        .getChildOfType<YAMLDocument>()
        .getChildOfType<YAMLMapping>()
        .getChildOfType<YAMLKeyValue>()
        .getChildOfType<YAMLSequence>()


    private fun PsiFile.getAllSubElements(
        sectionName: String,
        fieldName: String
    ): List<YAMLKeyValue> {
        return getEntityDeclarations().mapToFields(sectionName)
            .map{ it.getChildOfType<YAMLSequence>().mapToFields(fieldName) }.flatten()
    }

    private fun PsiFile.getAllKeys(
        sectionName: String,
        fieldName: String
    ): List<String> {
        return getAllSubElements(sectionName, fieldName).map { it.valueText.trim() }
    }

    private fun PsiFile.getAllFunctionKeysWithEntityId(): Set<String> {
        return getEntityDeclarations().mapToFields("functions")
            .map{
                it.findChildrenOfType<YAMLMapping>().map{
                    if (it.getKeyValueByKey("withEntityId")?.valueText.toBoolean()) {
                        it.getKeyValueByKey("key")?.valueText
                    } else {
                        null
                    }
                }.filterNotNull()
            }.flatten().toSet()
    }

    private fun PsiFile.getAllEventsKeys(): List<String> {
        return getAllSubElements("calendars", "events")
            .map { it.findChildOfType<YAMLSequence>() }
            .map{ it.mapToFields("key") }.flatten().map{ it.valueText }
    }

    fun invalidate(tenantName: String) {
        entityFiles.values.removeIf{it.tenantName == tenantName}
        filesByTenants.remove(tenantName)
        entityByTenants.remove(tenantName)
    }

}

data class XmEntitySpecInfo(
    val tenantName: String,
    val keys: List<YAMLKeyValue>,
    val functionKeys: List<String>,
    val functionKeysWithEntityId: Set<String>,
    val eventsKeys: List<String>,
    val linksKeys: List<String>,
    val attachmentsKeys: List<String>,
    val calendarsKeys: List<String>,
    val locationsKeys: List<String>,
    val tagsKeys: List<String>,
    val commentsKeys: List<String>,
    val ratingsKeys: List<String>
) {
    companion object {
        val NULL_OBJECT = XmEntitySpecInfo("NULL", emptyList(), emptyList(), emptySet(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }

}

class XmEntitySpecInfoBuilder(
    val tenantName: String,
    val keys: MutableList<YAMLKeyValue> = ArrayList(),
    val functionKeys: MutableList<String> = ArrayList(),
    val functionKeysWithEntityId: MutableSet<String> = HashSet(),
    val eventsKeys: MutableList<String> = ArrayList(),
    val linksKeys: MutableList<String> = ArrayList(),
    val attachmentsKeys: MutableList<String> = ArrayList(),
    val calendarsKeys: MutableList<String> = ArrayList(),
    val locationsKeys: MutableList<String> = ArrayList(),
    val tagsKeys: MutableList<String> = ArrayList(),
    val commentsKeys: MutableList<String> = ArrayList(),
    val ratingsKeys: MutableList<String> = ArrayList()
) {
    fun add(it: XmEntitySpecInfo) {
        keys.addAll(it.keys)
        functionKeys.addAll(it.functionKeys)
        functionKeysWithEntityId.addAll(it.functionKeysWithEntityId)
        eventsKeys.addAll(it.eventsKeys)
        linksKeys.addAll(it.linksKeys)
        attachmentsKeys.addAll(it.attachmentsKeys)
        calendarsKeys.addAll(it.calendarsKeys)
        locationsKeys.addAll(it.locationsKeys)
        tagsKeys.addAll(it.tagsKeys)
        commentsKeys.addAll(it.commentsKeys)
        ratingsKeys.addAll(it.ratingsKeys)
    }
    fun toXmEntitySpecInfo() = XmEntitySpecInfo(tenantName, keys, functionKeys, functionKeysWithEntityId, eventsKeys, linksKeys, attachmentsKeys,
        calendarsKeys, locationsKeys, tagsKeys, commentsKeys, ratingsKeys)
}

fun VirtualFile?.isEntitySpecification(): Boolean {
    this ?: return false
    val containingDirectory = this.parent
    val isEntitySpecDir = containingDirectory?.name?.equals("xmentityspec") ?: false
    val isYamlFile = name.endsWith(".yml")
    if (isEntitySpecDir && isYamlFile) {
        return true
    }
    val isEntityDir = containingDirectory?.name?.equals("entity") ?: return false
    val isEntitySpecFile = "xmentityspec.yml".equals(name)
    return isEntityDir && isEntitySpecFile
}
