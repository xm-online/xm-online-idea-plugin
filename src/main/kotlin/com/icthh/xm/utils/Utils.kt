package com.icthh.xm.utils

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

val loggers = ConcurrentHashMap<Class<Any>, Logger>()
val loggerFactory: (Class<Any>) -> Logger = { Logger.getInstance(it) }
val Any.log: Logger get() = loggers.computeIfAbsent(this.javaClass, loggerFactory)

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
