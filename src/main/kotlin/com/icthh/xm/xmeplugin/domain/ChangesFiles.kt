package com.icthh.xm.xmeplugin.domain

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import configPathToRealPath
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

data class ChangesFiles(
    val editedInThisIteration: Set<String> = emptySet(),
    val editedFromStart: Set<String> = emptySet(),
    val forUpdate: Set<String> = emptySet(),
    val changesFiles: Set<String> = emptySet(),
    val bigFiles: Set<String> = emptySet(),
    val toDelete: Set<String> = emptySet(),
    val updatedFileContent: MutableMap<String, InputStream> = HashMap(),
    val isForceUpdate: Boolean = false
) {
    fun forRegularUpdate(ignoredFiles: Set<String>) : Set<String> {
        var list = forUpdate.filterNot { it in bigFiles }
        if (!isForceUpdate) {
            list = list.filterNot { it in ignoredFiles }
        }
        return list.toSet()
    }

    fun getBigFilesForUpdate(ignoredFiles: Set<String>): Set<String> {
        var list = changesFiles.filter { it in bigFiles }
        if (!isForceUpdate) {
            list = list.filterNot { it in ignoredFiles }
        }
        return list.toSet()
    }

    fun toDelete(ignoredFiles: Set<String>): Set<String> {
        if (!isForceUpdate) {
            return toDelete.filterNot { it in ignoredFiles }.toSet()
        }
        return toDelete
    }

    fun refresh(project: Project) {
        updatedFileContent.keys.toList().forEach {
            updatedFileContent[it] = readFile(project, it)
        }
    }

    private fun readFile(project: Project, key: String): InputStream {
        val path = project.configPathToRealPath(key)
        val vf = VfsUtil.findFile(File(path).toPath(), true)
        val content = vf?.contentsToByteArray()
        content ?: return InputStream.nullInputStream()
        return ByteArrayInputStream(content)
    }

    fun updatedFiles(): List<FileChange> {
        return forRegularUpdate(emptySet()).filter{ updatedFileContent[it] != null }.map { FileChange(it, updatedFileContent[it]!!) }
    }

    data class FileChange(
        val path: String,
        val content: InputStream,
    )
}
