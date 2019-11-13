package com.icthh.xm.actions.deploy

import com.icthh.xm.service.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.io.InputStream


class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        val changesFiles = project.getChangedFiles()
        val map = HashMap<String, InputStream?>()
        changesFiles.forEach {
            val virtualFile = VfsUtil.findFileByURL(File(it).toURL())
            if (virtualFile != null) {
                map.put("/config/tenants" + virtualFile.getConfigRelatedPath(project),  virtualFile.inputStream)
            } else {
                map.put("/config/" + it.substringAfter(project.getConfigRootDir()), "".toByteArray().inputStream())
            }
        }

        getApplication().executeOnPooledThread{
            project.getExternalConfigService().updateInMemory(project, selected, map)
        }

    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project ?: return

        anActionEvent.presentation.isEnabled = project.getSettings().selected()?.trackChanges ?: false
    }
}
