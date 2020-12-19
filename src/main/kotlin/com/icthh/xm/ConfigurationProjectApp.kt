package com.icthh.xm

import com.icthh.xm.actions.settings.SettingsDialog
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.actions.shared.showNotificationWithAction
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.startTrackChanges
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.startDiagnostic
import com.intellij.ide.DataManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType.INFO
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager
import javax.swing.event.HyperlinkEvent
import com.intellij.openapi.actionSystem.DataConstants
import com.intellij.openapi.actionSystem.DataContext




class ConfigurationProjectApp: ProjectComponent {

    override fun initComponent() {
        //this.startDiagnostic()
    }

    override fun disposeComponent() {

    }

    override fun projectOpened() {
        VirtualFileManager.getInstance().addVirtualFileListener(object: VirtualFileContentsChangedAdapter() {
            override fun onFileChange(fileOrDirectory: VirtualFile) {
            }

            override fun onBeforeFileChange(fileOrDirectory: VirtualFile) {
                val dataContext = DataManager.getInstance().dataContext
                val project = dataContext.getData(DataConstants.PROJECT) as Project? ?: return
                if (!fileOrDirectory.path.startsWith(project.getConfigRootDir())) {
                    return
                }
                val selected = project.getSettings().selected()
                if (selected == null || !selected.startTrackChangesOnEdit.isTrue() || selected.updateMode.isGitMode) {
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
