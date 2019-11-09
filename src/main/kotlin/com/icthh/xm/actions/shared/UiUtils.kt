package com.icthh.xm.actions.shared

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project


fun Project.showNotification(dispayId: String, title: String, content: String?, notificationType: NotificationType) {
    ApplicationManager.getApplication().runReadAction {
        val notification = Notification(dispayId, title, content ?: "", notificationType)
        notification.notify(this)
    }
}
