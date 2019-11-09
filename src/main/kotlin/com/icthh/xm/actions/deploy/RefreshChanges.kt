package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.utils.getExternalConfigService
import com.icthh.xm.utils.getSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication

class RefreshChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project ?: return
        val selected = project.getSettings().selected() ?: return
        val externalConfigService = project.getExternalConfigService()

        getApplication().executeOnPooledThread{
            try {
                externalConfigService.refresh(selected)
            } catch (e: Exception) {
                project.showNotification("ERROR", "Error update configuration",
                    e.message, NotificationType.ERROR
                )
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.presentation.isEnabled = anActionEvent.project?.getSettings()?.selected() != null
    }
}
