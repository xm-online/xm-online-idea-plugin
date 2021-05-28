package com.icthh.xm.service.filechanges

import com.icthh.xm.actions.settings.UpdateMode
import com.icthh.xm.service.*
import com.icthh.xm.utils.logger
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.Repository.State.NORMAL
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.Change.Type.*
import com.intellij.openapi.vfs.VfsUtil
import git4idea.GitRevisionNumber.HEAD
import git4idea.changes.GitChangeUtils.getDiff
import git4idea.changes.GitChangeUtils.getDiffWithWorkingTree
import git4idea.repo.GitRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Future

class GitFileChange(
    val project: Project
): FileChange {

    override fun getChangedFiles(): ChangesFiles = with(project) {
        val selected = getSettings().selected()
        selected ?: return ChangesFiles()

        FileDocumentManager.getInstance().saveAllDocuments()

        val repository = project.getRepository()
        val root = root()
        if (repository?.state != NORMAL || root == null) {
            throw UncorrectStateOfRepository(repository?.state)
        }

        val localChanges = getChanges()
        val files = HashSet<String>()
        files.addAll(localChanges.map { it.afterRevision?.file?.path }.filterNotNull())
        files.addAll(localChanges.map { it.beforeRevision?.file?.path }.filterNotNull())
        files.addAll(selected.lastChangedFiles.map { toAbsolutePath(it) })
        return getChangedFiles(files)
    }

    fun Change.getPath() = beforeRevision?.file?.path ?: afterRevision?.file?.path

    override fun getChangedFiles(files: Set<String>, forceUpdate: Boolean): ChangesFiles = with(project) {
        val selected = getSettings().selected()
        selected ?: return ChangesFiles()

        val toDelete = LinkedHashSet<String>()
        val filesForUpdate = HashSet<String>()
        val bigFiles = LinkedHashSet<String>()
        val editedFromStart = LinkedHashSet<String>()
        val editedInThisIteration = LinkedHashSet<String>()
        val updatedFileContent: MutableMap<String, InputStream> = HashMap()

        val repository = project.getRepository() ?: return ChangesFiles()
        val root = root()
        if (repository.state != NORMAL || root == null) {
            throw UncorrectStateOfRepository(repository.state)
        }

        val localChanges = getChanges()
        val changes = localChanges.map { it.getPath() to it.type }.toMap()

        files.forEach {
            val file = VfsUtil.findFileByURL(File(it).toURL())
            val type = changes.get(it)
            if (type == DELETED || type == MOVED || file == null) {
                toDelete.add(project.toRelatedPath(it))
                return@forEach
            }

            val byteArray = file.contentsToByteArray()
            val relatedPath = file.getConfigRootRelatedPath(this)

            if (byteArray.isEmpty() || byteArray.size >= 1024 * 1024) {
                bigFiles.add(relatedPath)
            }

            if (changes.containsKey(it)) {
                editedInThisIteration.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }

            if (selected.lastChangedFiles.contains(relatedPath)) {
                editedFromStart.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }

            if (forceUpdate) {
                filesForUpdate.add(relatedPath)
                updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
            }
        }

        filesForUpdate.addAll(editedFromStart)
        filesForUpdate.addAll(editedInThisIteration)

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

    private fun Project.getChanges(): MutableCollection<Change> {
        try {
            return ApplicationManager.getApplication().executeOnPooledThread(Callable {
                val repository = this.getRepository() ?: return@Callable mutableListOf()
                val settings = project.getSettings()?.selected() ?: return@Callable mutableListOf()
                val changes = HashSet<Change>()
                if (settings.updateMode == UpdateMode.GIT_LOCAL_CHANGES) {
                    val branchName = repository.currentBranch?.name ?: HEAD.rev
                    changes.addAll(getDiffWithWorkingTree(repository, branchName, false) ?: mutableListOf())
                } else if (settings.updateMode == UpdateMode.GIT_BRANCH_DIFFERENCE) {
                    val currentBranch = repository.currentBranch?.name ?: HEAD.rev
                    changes.addAll(getDiffWithWorkingTree(repository, currentBranch, false) ?: mutableListOf())
                    changes.addAll(getDiff(repository, settings.branchName, currentBranch, false) ?: mutableListOf())
                }
                changes
            }).get()
        } catch (e: Throwable) {
            logger.info("Error ${e}")
            throw e;
        }
    }

}

class UncorrectStateOfRepository(val state: Repository.State?): Exception()
