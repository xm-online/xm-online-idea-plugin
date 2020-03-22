package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.shared.ConfirmDialog
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.isTrue
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
            settings?.atStartFilesState?.clear()
            settings?.ignoredFiles?.clear()
            settings?.lastChangedFiles?.clear()
            settings?.lastTimeTryToNotifyAboutDifference = 0
        }
        project.save()
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return

        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()

        anActionEvent.presentation.isVisible = !settings?.updateMode?.isGitMode.isTrue()
        if (settings?.updateMode?.isGitMode.isTrue()) {
            return
        }

        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges ?: false
    }
}
