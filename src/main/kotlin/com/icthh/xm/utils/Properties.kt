package com.icthh.xm.utils

import com.icthh.xm.actions.settings.SettingService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.lang.Boolean.TRUE

fun Project.getProperties() = PropertiesComponent.getInstance(this)

fun Project.getSettings() = ServiceManager.getService(this, SettingService::class.java)

fun Project.isConfigProject() = true

fun Project.getConfigRootDir() = this.basePath + "/config"

fun VirtualFile.getTenantRelatedPath(project: Project): String {
    return getPathRelatedTo(project, "/tenants")
}

fun VirtualFile.getConfigRelatedPath(project: Project): String {
    return getPathRelatedTo(project)
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

fun Boolean?.isTrue() = TRUE.equals(this)
