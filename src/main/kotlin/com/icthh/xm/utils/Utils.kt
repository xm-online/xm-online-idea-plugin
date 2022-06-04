package com.icthh.xm.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.lang3.time.StopWatch
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicLong


val loggers = ConcurrentHashMap<Class<Any>, Logger>()
val loggerFactory: (Class<Any>) -> Logger = { Logger.getInstance(it) }
val Any.log: Logger get() = loggers.computeIfAbsent(this.javaClass, loggerFactory)
val Any.logger get() = java.util.logging.Logger.getLogger(this.javaClass.name)
val YAML_MAPPER = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}

fun showDiffDialog(windowTitle: String, content: String, title1: String,
                   title2: String, project: Project, file: VirtualFile) {
    val content1 = DiffContentFactory.getInstance().create(content)
    val content2 = DiffContentFactory.getInstance().create(project, file)
    val request = SimpleDiffRequest(windowTitle, content1, content2, title1, title2)
    DiffManager.getInstance().showDiff(project, request)
}

fun String?.templateOrEmpty(template: (String) -> String): String {
    this ?: return ""
    return template.invoke(this)
}

fun <T> difference(left: Set<T>, right: Set<T>): Set<T> {
    val first = HashSet(left)
    val second = HashSet(right)
    val roles = first.filter { second.contains(it) }
    first.removeAll(roles)
    second.removeAll(roles)
    val difference = HashSet<T>()
    difference.addAll(first)
    difference.addAll(second)
    return difference
}

val times: MutableMap<String, AtomicLong> = ConcurrentHashMap()
val timers: MutableMap<String, StopWatch> = ConcurrentHashMap()
fun start(key: String) {
    timers.put(key, StopWatch.createStarted())
}

fun stop(key: String) {
    val nanoTime = timers.get(key)?.getNanoTime() ?: 0
    times.computeIfAbsent(key) { AtomicLong() }
    times.get(key)?.addAndGet(nanoTime)
}

fun Any.startDiagnostic() {
    Thread {
        while(!Thread.interrupted()) {
            Thread.sleep(5000)
            val line = StringBuilder()
            times.forEach {
                line.append(it.key, " ", it.value.get() / 1000_000, "\n")
            }
            logger.info(line.toString())
        }
    }.start()
}

fun <R> doPseudoAsync(operation: Callable<R>):R {
    val futureTask = FutureTask<R>(operation)
    val t = Thread(futureTask)
    t.start()
    return futureTask.get()
}

fun doAsync(action: Runnable) {
    getApplication().executeOnPooledThread(action)
}

fun File.deleteSymlink() {
    walkTopDown().forEach {
        if (Files.isSymbolicLink(it.toPath())) {
            it.delete()
            it.parentFile?.delete()
            it.parentFile?.parentFile?.delete()
        }
    }
    VfsUtil.findFile(toPath(), false)?.refresh(false, false)
    delete()
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

fun String?.ifNullOrBlank(value: String) = if (this.isNullOrBlank()) {
    value
} else {
    this
}

