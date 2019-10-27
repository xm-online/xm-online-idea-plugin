package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

import com.intellij.openapi.actionSystem.PlatformDataKeys
import org.apache.commons.codec.digest.DigestUtils


class TrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        project.getSettings()?.selected()?.trackChanges = true
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project

        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges?.not() ?: true

        if (settings != null && settings.trackChanges) {
            val vFile = anActionEvent.getData(PlatformDataKeys.VIRTUAL_FILE)
            val editedFiles = settings.editedFiles
            if (vFile != null && !editedFiles.contains(vFile.name)) {
                editedFiles.put(vFile.getName(), DigestUtils.sha256Hex(vFile.inputStream))
            }
        }

    }
}
