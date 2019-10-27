package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

import javax.swing.*
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils


class TrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        project.getSettings().trackChanges = true
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
        anActionEvent.presentation.isVisible = project?.getSettings()?.trackChanges?.not() ?: false

        project ?: return
        if (project.getSettings().trackChanges) {
            val vFile = anActionEvent.getData(PlatformDataKeys.VIRTUAL_FILE)
            val editedFiles = project.getSettings().editedFiles
            if (vFile != null && !editedFiles.contains(vFile.name)) {
                editedFiles.put(vFile.getName(), DigestUtils.sha256Hex(vFile.inputStream))

            }
        }

    }
}
