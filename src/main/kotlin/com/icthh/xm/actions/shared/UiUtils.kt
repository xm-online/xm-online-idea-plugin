package com.icthh.xm.actions.shared

import com.icthh.xm.actions.settings.MainSettingAction
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon.Position.*
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint.*
import javax.swing.event.HyperlinkEvent


fun Project.showNotification(dispayId: String, title: String, notificationType: NotificationType,
                             content: () -> String) {
    ApplicationManager.getApplication().runReadAction {
        val notification = Notification("XM plugin " + dispayId, title, content.invoke(), notificationType)
        notification.notify(this)
    }
}

fun Project.showNotificationWithAction(dispayId: String, title: String, notificationType: NotificationType, action: NotificationListener,
                             content: () -> String) {
    ApplicationManager.getApplication().runReadAction {
        val notification = Notification("XM plugin " + dispayId, title, content.invoke(), notificationType, action)
        notification.notify(this)
        notification.setImportant(true)
    }
}

fun Project.showMessage(messageType: MessageType, htmlText: () -> String) {
    ApplicationManager.getApplication().invokeLater {

        val statusBar = WindowManager.getInstance().getStatusBar(this)

        var component = statusBar.component

        component = WindowManager.getInstance().getFrame(this)?.jMenuBar

        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(htmlText.invoke(), messageType, null)
            .setFadeoutTime(10_000)
            .createBalloon()
            .show(getNorthEastOf(component), above)

    }
}
