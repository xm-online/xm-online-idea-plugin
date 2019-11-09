package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.shared.ConfirmDialog
import com.icthh.xm.utils.getSettings
import com.icthh.xm.utils.updateSupported
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


class StopTrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return

        val dialog = ConfirmDialog("Are you sure?", "Information about changes will be lost.")
        dialog.show()
        if (dialog.isOK) {
            val settings = project.getSettings().selected()
            settings?.trackChanges = false
            settings?.editedFiles?.clear()
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return

        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges ?: false
    }
}
