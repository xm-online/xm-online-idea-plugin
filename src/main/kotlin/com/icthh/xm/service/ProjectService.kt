package com.icthh.xm.service

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.FileState
import com.icthh.xm.actions.settings.SettingService
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.filechanges.ChangesFiles
import com.icthh.xm.service.filechanges.GitFileChange
import com.icthh.xm.service.filechanges.MemoryFileChange
import com.icthh.xm.utils.doPseudoAsync
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.readTextAndClose
import com.intellij.history.LocalHistory
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import git4idea.GitUtil.getRepositoryForRoot
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import git4idea.repo.GitRepositoryManager
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.jetbrains.annotations.NotNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Future
import kotlin.streams.asSequence

const val CONFIG_DIR_NAME = "/config"

fun Project.getSettings() = ServiceManager.getService(this, SettingService::class.java)
fun Project.getExternalConfigService() = ServiceManager.getService(this, ExternalConfigService::class.java)
fun Project?.isConfigProject(): Boolean {
    return this != null && isConfigRoot(this.basePath)
}

fun Project.getLinkedConfigRootDir() = if (isConfigProject()) {
    getConfigRootDir()
} else {
    "${basePath}/src/main/lep"
}

fun Project.getConfigRootDir() = if (isConfigProject()) {
    toConfigFolder(basePath)
} else {
    toConfigFolder(getSettings().selected()?.basePath)
}

private fun toConfigFolder(basePath: String?) = basePath + CONFIG_DIR_NAME

fun isConfigRoot(path: String?): Boolean {
    return  File(toConfigFolder(path)).exists() && File(toConfigFolder(path) + "/tenants/tenants-list.json").exists()
}

fun Project?.isSupportProject() = isConfigProject() || isEntityProject()
fun Project?.isEntityProject() = this?.name == "xm-ms-entity"

fun Project?.projectType(): String {
    if (isConfigProject()) {
        return "CONFIG"
    } else if (isEntityProject()) {
        return "ENTITY"
    } else {
        return "UNKNOWN"
    }
}

fun Project.configPathToRealPath(configPath: String): String {
    val selected = this.getSettings().selected()
    if (!selected?.isConfigProject.isTrue()) {
        return selected?.basePath.let { it + configPath }
    }
    return this.basePath + configPath
}

fun Project.toAbsolutePath(relatedPath: String) = configPathToRealPath(toRelatedPath(relatedPath))
fun Project.toRelatedPath(absolutePath: String): String {
    if (absolutePath.startsWith(getConfigRootDir())) {
        return CONFIG_DIR_NAME + absolutePath.substringAfter(getConfigRootDir())
    } else if (absolutePath.startsWith(getLinkedConfigRootDir())) {
        return CONFIG_DIR_NAME + "/tenants" + absolutePath.substringAfter(getLinkedConfigRootDir())
    } else {
        return absolutePath
    }
}

fun Project.getRepository(onlyProject: Boolean = false): GitRepository? {
    val repositoryManager = GitRepositoryManager.getInstance(this)
    val repos = repositoryManager.getRepositories()
    val root = root()
    val path = root?.path
    var repo = repos.findLast { it.root.path == path }
    if (onlyProject) {
        return repo
    }
    if (repo == null) {
        doPseudoAsync {
            repo = repositoryManager.getRepositoryForRoot(root)
        }
    }
    if (repo == null && root != null) {
        repositoryManager.addExternalRepository(root, GitRepositoryImpl.createInstance(root, this, this, true))
        doPseudoAsync {
            repo = repositoryManager.getRepositoryForRoot(root)
        }
    }
    return repo
}

class RepositoryNotFound : Exception()

fun Project.root() =
    VfsUtil.findFile(File(this.getConfigRootDir()).toPath(), true)?.parent

fun Project.saveCurrentFileStates() {
    val settings = getSettings()?.selected()
    settings ?: return
    val notified = settings.editedFiles.filter { it.value.isNotified }.keys
    val editedFiles = HashMap<String, FileState>()
    allFilesStream().forEach{
        editedFiles.put(it.systemIndependentPath, FileState(sha256Hex(it.inputStream())))
    }
    notified.forEach {
        editedFiles[it]?.isNotified = true
    }
    settings.editedFiles = editedFiles
    ApplicationManager.getApplication().invokeLater {
        ApplicationManager.getApplication().runWriteAction {
            save()
        }
    }
}

fun Project.startTrackChanges(): Boolean {
    val settings = getSettings()?.selected()
    settings ?: return false

    if (settings.trackChanges) {
        return false
    }

    settings.trackChanges = true

    saveCurrentFileStates()
    settings.atStartFilesState = settings.editedFiles

    LocalHistory.getInstance().putUserLabel(this, "CHANGES_FROM_${settings.id}");
    this.save()
    ApplicationManager.getApplication().executeOnPooledThread {
        settings.version = getVersion(settings)
    }
    return true
}

private fun Project.getVersion(settings: EnvironmentSettings): String {
    try {
        return getExternalConfigService().getCurrentVersion(settings)
    } catch (e: Exception) {
        showNotification("getVersion", "Error get current version of config", NotificationType.ERROR) {
            e.message ?: ""
        }
        throw e;
    }
}

fun Project.allPaths() = allFilesStream().map { it.systemIndependentPath }.toList().toMutableSet()

fun VirtualFile.getTenantRelatedPath(project: Project): String {
    return getPathRelatedTo(project, "/tenants")
}

fun VirtualFile.getConfigRelatedPath(project: Project): String {
    return getPathRelatedTo(project)
}

fun VirtualFile.getTenantName(project: Project): String {
    return getConfigRelatedPath(project).substringAfter("/").substringBefore("/")
}

fun VirtualFile.getConfigRootRelatedPath(project: Project): String {
    return "/config/tenants" + getPathRelatedTo(project)
}

fun VirtualFile.isConfigFile(project: Project): Boolean {
    return this.path.startsWith(project.getConfigRootDir()) || this.path.startsWith(project.getLinkedConfigRootDir())
}

fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

private fun VirtualFile.getPathRelatedTo(
    project: Project,
    root: String = ""
): String {
    var vFile = this;

    while (vFile.parent != null && !vFile.parent.path.equals(project.getConfigRootDir() + root)) {
        vFile = vFile.parent
    }

    return this.path.substring(vFile.path.length)
}

fun AnActionEvent.updateSupported(): Boolean? {
    presentation.isVisible = project?.isSupportProject() ?: false
    return if (presentation.isVisible) true else null
}


fun Project.allFilesStream() = Files.walk(Paths.get(getConfigRootDir())).asSequence().filter { !it.isDirectory() }

fun Project.getChangedFiles(): ChangesFiles {
    val selected = getSettings().selected()
    selected ?: return ChangesFiles()

    if (selected.updateMode.isGitMode) {
        return GitFileChange(this).getChangedFiles()
    } else {
        return MemoryFileChange(this).getChangedFiles()
    }
}

fun Project.getChangedFiles(files: Set<String>, forceUpdate: Boolean = false): ChangesFiles {
    val selected = getSettings().selected()
    selected ?: return ChangesFiles()

    if (!selected.updateMode.isGitMode) {
        return MemoryFileChange(this).getChangedFiles(files, forceUpdate)
    }
    if (selected.updateMode.isGitMode) {
        return GitFileChange(this).getChangedFiles(files, forceUpdate)
    }
    return ChangesFiles()
}

fun Project.updateFilesInMemory(changesFiles: ChangesFiles, selected: EnvironmentSettings): Future<*> {
    selected.lastChangedFiles.clear()
    selected.lastChangedFiles.addAll(changesFiles.editedInThisIteration)
    selected.lastChangedFiles.removeAll(selected.ignoredFiles)
    save()
    return ApplicationManager.getApplication().executeOnPooledThread {
        val regularUpdateFiles = changesFiles.forRegularUpdate(selected.ignoredFiles)
        if (regularUpdateFiles.isNotEmpty()) {
            val map = changesFiles.updatedFileContent.filterKeys { it in regularUpdateFiles}
            this.getExternalConfigService().updateInMemory(this, selected, map)
        }
        val toDelete = changesFiles.toDelete(selected.ignoredFiles)
        if (toDelete.isNotEmpty()) {
            this.getExternalConfigService().deleteConfig(this, selected, toDelete)
        }
        changesFiles.getBigFilesForUpdate(selected.ignoredFiles).forEach {
            val content = changesFiles.updatedFileContent[it]?.readTextAndClose() ?: ""
            this.getExternalConfigService().updateFileInMemory(this, selected, it, content)
        }
        this.showMessage(MessageType.INFO) {
            "Configs successfully update"
        }
        this.saveCurrentFileStates()
    }
}

fun GitRepository?.getLocalBranches(): List<String> {
    val repository = this?.project?.getRepository() ?: return listOf()
    return repository.branches.localBranches.map { it.name }.filter { repository.currentBranch?.name != it }
}
