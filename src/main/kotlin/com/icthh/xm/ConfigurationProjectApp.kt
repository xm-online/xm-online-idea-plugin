package com.icthh.xm

import com.icthh.xm.actions.settings.MainSettingAction
import com.icthh.xm.actions.settings.SettingsDialog
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.actions.shared.showNotificationWithAction
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.startTrackChanges
import com.icthh.xm.utils.isTrue
import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.ui.MessageType.INFO
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager
import javax.swing.event.HyperlinkEvent


class ConfigurationProjectApp: ProjectComponent {

    override fun initComponent() {

    }

    override fun disposeComponent() {

    }

    override fun projectOpened() {
        VirtualFileManager.getInstance().addVirtualFileListener(object: VirtualFileContentsChangedAdapter() {
            override fun onFileChange(fileOrDirectory: VirtualFile) {
            }

            override fun onBeforeFileChange(fileOrDirectory: VirtualFile) {
                val dataContext = DataManager.getInstance().dataContext
                val project = DataKeys.PROJECT.getData(dataContext) ?: return
                if (!fileOrDirectory.path.startsWith(project.getConfigRootDir())) {
                    return
                }
                if (!project.getSettings().selected()?.startTrackChangesOnEdit.isTrue()) {
                    return
                }
                if (!project.startTrackChanges()) {
                    return
                }
                project.showMessage(INFO) {
                    "Track changes enabled"
                }
                project.showNotificationWithAction("Track changes", "Track changes started",
                    INFORMATION, NotificationListener { notification, hyperlinkEvent ->
                        if (hyperlinkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            val dialog = SettingsDialog(project)
                            dialog.show()
                            if (dialog.isOK) {
                                project.getSettings().envs.clear()
                                project.getSettings().envs.addAll(dialog.data)
                            }
                            project.save()
                        }
                    }
                ) {
                    """
                                Track changes started by edit file event. For changes settings go to 
                                <a href="settings" target="blank">setting</a>
                            """
                }
            }
        })
    }

    override fun projectClosed() {

    }

}
