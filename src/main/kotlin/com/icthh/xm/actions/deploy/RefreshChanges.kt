package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.SettingsDialog
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.getExternalConfigService
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.updateSupported
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.ui.MessageType.INFO

class RefreshChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project ?: return
        val selected = project.getSettings().selected() ?: return
        val externalConfigService = project.getExternalConfigService()

        getApplication().executeOnPooledThread{
            try {
                externalConfigService.refresh(selected)
                project.showMessage(INFO) {
                    """
                        Configuration updated
                    """.trimIndent()
                }
                if (selected.updateMode.isGitMode) {
                    selected.lastChangedFiles.clear()
                }
            } catch (e: Exception) {
                project.showNotification("ERROR", "Error update configuration", ERROR) {
                    e.message ?: ""
                }
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        anActionEvent.presentation.isEnabled = anActionEvent.project?.getSettings()?.selected() != null
    }
}
