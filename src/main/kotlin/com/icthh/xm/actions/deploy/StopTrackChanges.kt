package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.SettingsDialog
import com.icthh.xm.actions.shared.ConfirmDialog
import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


class StopTrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return

        val dialog = ConfirmDialog("Are you sure?", "Information about changes will be lost.")
        dialog.show()
        if (dialog.isOK) {
            project.getSettings().trackChanges = false
            project.getSettings().editedFiles.clear()
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
        anActionEvent.presentation.isVisible = project?.getSettings()?.trackChanges ?: false
    }
}
