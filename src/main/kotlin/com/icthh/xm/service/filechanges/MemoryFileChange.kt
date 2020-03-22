package com.icthh.xm.service.filechanges

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.UpdateMode
import com.icthh.xm.service.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class MemoryFileChange(
    val project: Project
): FileChange {

    override fun getChangedFiles(): ChangesFiles = with(project) {
        val selected = getSettings().selected()
        selected ?: return ChangesFiles()

        FileDocumentManager.getInstance().saveAllDocuments()

        val allFiles = allPaths()
        allFiles.addAll(selected.editedFiles.keys)
        allFiles.addAll(selected.atStartFilesState.keys)
        return getChangedFiles(allFiles)
    }

    override fun getChangedFiles(files: Set<String>, forceUpdate: Boolean): ChangesFiles = with(project) {
        val selected = getSettings().selected()
        selected ?: return ChangesFiles()

        val toDelete = LinkedHashSet<String>()
        val filesForUpdate = HashSet<String>()
        val bigFiles = LinkedHashSet<String>()
        val editedFromStart = LinkedHashSet<String>()
        val editedInThisIteration = LinkedHashSet<String>()
        val updatedFileContent: MutableMap<String, InputStream> = HashMap()

        files.forEach {
            val file = VfsUtil.findFileByURL(File(it).toURL())
            if (file == null) {
                toDelete.add(project.toRelatedPath(it))
                return@forEach
            }

            val byteArray = file.contentsToByteArray()
            val relatedPath = file.getConfigRootRelatedPath(this)

            if (byteArray.isEmpty() || byteArray.size >= 1024 * 1024) {
                bigFiles.add(relatedPath)
            }

            val sha256Hex = DigestUtils.sha256Hex(byteArray)
            if (selected.editedFiles.isNotEmpty() && selected.editedFiles[it]?.sha256 != sha256Hex) {
                editedInThisIteration.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }
            if (isEditedFromStart(selected, it, relatedPath, sha256Hex)) {
                editedFromStart.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }
            if (forceUpdate) {
                filesForUpdate.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }
        }

        if (selected.updateMode == UpdateMode.INCREMENTAL) {
            filesForUpdate.addAll(editedInThisIteration)
        } else {
            filesForUpdate.addAll(editedFromStart)
            filesForUpdate.addAll(editedInThisIteration)
        }

        val changesFiles = ArrayList<String>().union(filesForUpdate).union(toDelete)
        return ChangesFiles(
            editedInThisIteration,
            editedFromStart,
            filesForUpdate,
            changesFiles,
            bigFiles,
            toDelete,
            updatedFileContent,
            forceUpdate
        )
    }

    private fun isEditedFromStart(selected: EnvironmentSettings, filePath: String, relatedPath: String, sha256Hex: String?): Boolean {
        val asStartFilesState = selected.atStartFilesState
        return (selected.lastChangedFiles.contains(relatedPath) || (asStartFilesState.isNotEmpty() && asStartFilesState[filePath]?.sha256 != sha256Hex))
    }

}