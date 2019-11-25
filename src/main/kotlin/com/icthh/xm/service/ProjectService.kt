package com.icthh.xm.service

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.FileState
import com.icthh.xm.actions.settings.SettingService
import com.icthh.xm.actions.settings.UpdateMode.INCREMENTAL
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.utils.readTextAndClose
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
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

fun Project.allFilesStream() = Files.walk(Paths.get(getConfigRootDir())).asSequence().filter { !it.isDirectory() }

fun Project.getChangedFiles(): List<String> {
    val selected = getSettings().selected()
    selected ?: return emptyList()

    FileDocumentManager.getInstance().saveAllDocuments()

    val editedFiles = if (selected.updateMode == INCREMENTAL) selected.editedFiles else selected.atStartFilesState
    val changed = editedFiles.filter {
        val file = VfsUtil.findFileByURL(File(it.key).toURL())
        file ?: return@filter true
        sha256Hex(file.contentsToByteArray()) != it.value.sha256
    }.map { it.key }.toMutableSet()

    addNewFiels(editedFiles, changed)
    addNewFiels(selected.editedFiles, changed)

    return changed.toList()
}

private fun Project.addNewFiels(
    editedFiles: MutableMap<String, FileState>,
    changed: MutableSet<String>
) {
    val files = allPaths()
    files.removeAll(editedFiles.keys)
    changed.addAll(files)
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

fun Project.updateFilesInMemory(changesFiles: List<String>, selected: EnvironmentSettings): Future<*> {
    val toDelete = ArrayList<String>()
    val individualFiles = ArrayList<String>()
    val map = HashMap<String, InputStream?>()

    changesFiles.forEach {
        val virtualFile = VfsUtil.findFileByURL(File(it).toURL())
        if (virtualFile != null) {
            val content = virtualFile.inputStream.readTextAndClose()
            if (content.isEmpty() || content.length >= 1024 * 1024) {
                individualFiles.add(virtualFile.getConfigRootRelatedPath(this))
            } else {
                map.put(virtualFile.getConfigRootRelatedPath(this), virtualFile.inputStream)
            }
        } else {
            toDelete.add("/config" + it.substringAfter(this.getConfigRootDir()))
        }
    }

    return ApplicationManager.getApplication().executeOnPooledThread {
        if (map.isNotEmpty()) {
            this.getExternalConfigService().updateInMemory(this, selected, map)
        }
        if (toDelete.isNotEmpty()) {
            this.getExternalConfigService().deleteConfig(this, selected, toDelete)
        }
        individualFiles.forEach {
            this.getExternalConfigService().updateFileInMemory(this, selected, it, "")
        }
        this.showMessage(MessageType.INFO) {
            "Configs successfully update"
        }
        this.saveCurrectFileStates()
    }
}
