package com.icthh.xm.actions.deploy

import com.icthh.xm.service.*
import com.icthh.xm.utils.readTextAndClose
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
        val toDelete = ArrayList<String>()
        val emptyFiles = ArrayList<String>()
        val map = HashMap<String, InputStream?>()
        changesFiles.forEach {
            val virtualFile = VfsUtil.findFileByURL(File(it).toURL())
            if (virtualFile != null) {
                if (virtualFile.inputStream.readTextAndClose().isEmpty()) {
                    emptyFiles.add(virtualFile.getConfigRootRelatedPath(project))
                } else {
                    map.put(virtualFile.getConfigRootRelatedPath(project), virtualFile.inputStream)
                }
            } else {
                toDelete.add("/config/" + it.substringAfter(project.getConfigRootDir()))
            }
        }

        getApplication().executeOnPooledThread{
            if (map.isNotEmpty()) {
                project.getExternalConfigService().updateInMemory(project, selected, map)
            }
            // TODO delete
            emptyFiles.forEach {
                project.getExternalConfigService().updateFileInMemory(project, selected, it, "")
            }
        }

    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project ?: return

        anActionEvent.presentation.isEnabled = project.getSettings().selected()?.trackChanges ?: false
    }
}
