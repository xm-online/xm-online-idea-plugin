package com.icthh.xm.xmeplugin.utils

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiFileFactory.ORIGINAL_FILE
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.ConcurrentHashMap
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import java.util.UUID.randomUUID

val cacheKeys = ConcurrentHashMap<String, Key<*>>()
val UUID: Key<String> = Key.create("Unique.xme.id")

fun UserDataHolder.uuid(): String {
    var uuid = getUserData(UUID)
    if (uuid == null) {
        uuid = randomUUID().toString()
        putUserData(UUID, uuid)
    }
    return uuid
}

fun <T> PsiElement.withCache(key: String, compute: () -> T): T {
    val cacheKey = buildCacheKey<T>(key + uuid())
    return CachedValuesManager.getManager(project).getCachedValue(this, cacheKey, {
        CachedValueProvider.Result.create(compute.invoke(), listOf(this))
    }, false)
}

fun <T> buildCacheKey(key: String): Key<CachedValue<T>> {
    return cacheKeys.computeIfAbsent(key) { Key.create<CachedValue<T>>(key) } as Key<CachedValue<T>>
}

fun <T> PsiElement.withMultipleFilesCache(
    key: String,
    files: Collection<PsiFile>,
    compute: () -> T
): T {
    val cacheKey = buildCacheKey<T>(key + uuid())
    val sortedDependencies = files.sortedBy { it.virtualFile.path }.toTypedArray()
    return CachedValuesManager.getManager(this.project).getCachedValue(this, cacheKey, {
        CachedValueProvider.Result.create(compute(), *sortedDependencies)
    }, false)
}

fun <T> Project.withMultipleFilesCache(
    key: String,
    files: Collection<PsiFile>,
    compute: () -> T
): T {
    val cacheKey = buildCacheKey<T>(key + uuid())
    val sortedDependencies = files.sortedBy { it.virtualFile.path }.toTypedArray()
    return CachedValuesManager.getManager(this).getCachedValue(this, cacheKey, {
        CachedValueProvider.Result.create(compute(), *sortedDependencies)
    }, false)
}


fun PsiClass.getCountSubstring(): Int {
    val search = "$$"
    var index = 0
    var count = 0
    while (index >= 0) {
        index = this.name?.indexOf(search, index + 1) ?: -1
        count++
    }
    return count
}

inline fun <reified T : PsiElement> psiElement() = PlatformPatterns.psiElement(T::class.java)

inline fun <reified T : PsiElement> PsiElement.childrenOfType(): List<T> {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
}

fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

fun VirtualFile.toPsiElement(project: Project): PsiFileSystemItem? {
    val manager = PsiManager.getInstance(project)
    return manager.findFile(this) ?: manager.findDirectory(this)
}

inline fun <reified T : PsiElement> PsiElement?.findChildOfType() = PsiTreeUtil.findChildOfType(this, T::class.java)
inline fun <reified T : PsiElement> PsiElement?.findChildrenOfType() =
    PsiTreeUtil.findChildrenOfType(this, T::class.java)

inline fun <reified T: PsiElement> PsiElement?.getParentOfType() = PsiTreeUtil.getParentOfType(this, T::class.java)
inline fun <reified T: PsiElement> PsiElement?.getChildOfType() = PsiTreeUtil.getChildOfType(this, T::class.java)
inline fun <reified T: PsiElement> PsiElement?.getChildrenOfType(): MutableList<T> = getChildrenOfTypeAsList(this, T::class.java)

val PsiElement.originalFile: PsiFile
    get() {
        var originalFile = getUserData(ORIGINAL_FILE)
        if (originalFile == null) {
            originalFile = containingFile.originalFile
            putUserData(ORIGINAL_FILE, originalFile)
        }
        return originalFile
    }

fun PsiFile.containerFile(): PsiFile {
    val virtualFile = this.virtualFile
    val file = if (virtualFile is VirtualFileWindow) {
        virtualFile.delegate.toPsiFile(this.project)?.originalFile!!
    } else {
        this
    }
    return file
}

fun PsiFile.virtualFile(): VirtualFile {
    return this.virtualFile ?: this.originalFile.virtualFile
}

fun YAMLKeyValue?.keyTextMatches(key: String): Boolean {
    return this?.key?.textMatches(key) ?: false
}

fun YAMLSequence?.getKeys(): List<YAMLKeyValue> {
    return mapToFields("key")
}

fun YAMLSequence?.mapToFields(fieldName: String): List<YAMLKeyValue> {
    val yamlSequence = this
    return yamlSequence.getChildrenOfType<YAMLSequenceItem>()
        .map { it.getChildOfType<YAMLMapping>() }
        .map { it.getChildrenOfType<YAMLKeyValue>() }
        .flatten()
        .filter { it.keyTextMatches(fieldName) }
}

fun PsiReferenceRegistrar.registerProvider(pattern: ElementPattern<out PsiElement?>,
                                           work: (scalar: PsiElement, context: ProcessingContext) -> Array<PsiReference>) {
    registerReferenceProvider(pattern, object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext) = work.invoke(element, context)
    })
}

class PsiReferenceImpl(from: PsiElement, val to: PsiElement?): PsiReferenceBase<PsiElement>(from, true) {
    override fun resolve() = to
}

