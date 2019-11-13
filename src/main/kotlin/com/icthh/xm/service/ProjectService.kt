package com.icthh.xm.service

import com.icthh.xm.actions.settings.SettingService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.io.File

fun Project.getProperties() = PropertiesComponent.getInstance(this)

fun Project.getSettings() = ServiceManager.getService(this, SettingService::class.java)
fun Project.getExternalConfigService() = ServiceManager.getService(this, ExternalConfigService::class.java)
fun Project?.isConfigProject() = this != null
fun Project?.isSupportProject() = true
fun Project.getConfigRootDir() = this.basePath + "/config"


fun Project.getChangedFiles(): List<String> {
    val selected = getSettings().selected()
    selected ?: return emptyList()

    FileDocumentManager.getInstance().saveAllDocuments()

    val changed = selected.editedFiles.filter {
        val file = VfsUtil.findFileByURL(File(it.key).toURL())
        file ?: return@filter true
        sha256Hex(file.contentsToByteArray()) != it.value.sha256
    }.map { it.key }
    return changed
}



fun VirtualFile.getTenantRelatedPath(project: Project): String {
    return getPathRelatedTo(project, "/tenants")
}

fun VirtualFile.getConfigRelatedPath(project: Project): String {
    return getPathRelatedTo(project)
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
