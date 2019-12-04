package com.icthh.xm.service

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.FileState
import com.icthh.xm.actions.settings.SettingService
import com.icthh.xm.actions.settings.UpdateMode.INCREMENTAL
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.utils.readTextAndClose
import com.intellij.history.LocalHistory
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Future
import kotlin.streams.asSequence

fun Project.getProperties() = PropertiesComponent.getInstance(this)

fun Project.getSettings() = ServiceManager.getService(this, SettingService::class.java)
fun Project.getExternalConfigService() = ServiceManager.getService(this, ExternalConfigService::class.java)
fun Project?.isConfigProject(): Boolean {
    return this != null
            &&
            File(getConfigRootDir()).exists()
            &&
            File(getConfigRootDir() + "/tenants/tenants-list.json").exists()
}
fun Project?.isSupportProject() = isConfigProject()
fun Project.getConfigRootDir() = this.basePath + "/config"
fun Project.configPathToRealPath(configPath: String): String {
    return this.basePath + configPath
}

fun Project.saveCurrectFileStates() {
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

    saveCurrectFileStates()
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
    return this.path.startsWith(project.getConfigRootDir())
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

    FileDocumentManager.getInstance().saveAllDocuments()

    val allFiles = allPaths()
    allFiles.addAll(selected.editedFiles.keys)
    allFiles.addAll(selected.atStartFilesState.keys)
    return getChangedFiles(allFiles)
}

fun Project.getChangedFiles(files: Set<String>, forceUpdate: Boolean = false): ChangesFiles {
    val selected = getSettings().selected()
    selected ?: return ChangesFiles()

    val toDelete = LinkedHashSet<String>()
    val filesForUpdate = HashSet<String>()
    val bigFiles = LinkedHashSet<String>()
    val editedFromStart = LinkedHashSet<String>()
    val editedInThisIteration = LinkedHashSet<String>()
    val updatedFileContent = HashMap<String, ByteArrayInputStream>()

    files.forEach {
        val file = VfsUtil.findFileByURL(File(it).toURL())
        if (file == null) {
            toDelete.add("/config" + it.substringAfter(this.getConfigRootDir()))
            return@forEach
        }

        val byteArray = file.contentsToByteArray()
        val relatedPath = file.getConfigRootRelatedPath(this)

        if (byteArray.isEmpty() || byteArray.size >= 1024 * 1024) {
            bigFiles.add(relatedPath)
        }

        val sha256Hex = sha256Hex(byteArray)
        if (selected.editedFiles[it]?.sha256 != sha256Hex) {
            editedInThisIteration.add(relatedPath)
            updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
        }
        if (selected.atStartFilesState[it]?.sha256 != sha256Hex) {
            editedFromStart.add(relatedPath)
            updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
        }
        if (forceUpdate) {
            filesForUpdate.add(relatedPath)
            updatedFileContent.put(relatedPath, ByteArrayInputStream(byteArray))
        }
    }

    if (selected.updateMode == INCREMENTAL) {
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

data class ChangesFiles(
    val editedInThisIteration: Set<String> = emptySet(),
    val editedFromStart: Set<String> = emptySet(),
    val forUpdate: Set<String> = emptySet(),
    val changesFiles: Set<String> = emptySet(),
    val bigFiles: Set<String> = emptySet(),
    val toDelete: Set<String> = emptySet(),
    val updatedFileContent: Map<String, InputStream> = emptyMap(),
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
}

fun Project.updateFilesInMemory(changesFiles: ChangesFiles, selected: EnvironmentSettings): Future<*> {

    return ApplicationManager.getApplication().executeOnPooledThread {
        val regularUpdateFiles = changesFiles.forRegularUpdate(selected.ignoredFiles)
        if (regularUpdateFiles.isNotEmpty()) {
            val map = changesFiles.updatedFileContent.filterKeys { it in regularUpdateFiles}
            this.getExternalConfigService().updateInMemory(this, selected, map)
        }
        if (changesFiles.toDelete.isNotEmpty()) {
            this.getExternalConfigService().deleteConfig(this, selected, changesFiles.toDelete.toList())
        }
        changesFiles.getBigFilesForUpdate(selected.ignoredFiles).forEach {
            val content = changesFiles.updatedFileContent[it]?.readTextAndClose() ?: ""
            this.getExternalConfigService().updateFileInMemory(this, selected, it, content)
        }
        this.showMessage(MessageType.INFO) {
            "Configs successfully update"
        }
        this.saveCurrectFileStates()
    }
}
