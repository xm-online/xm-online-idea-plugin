package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.FileState
import com.icthh.xm.service.ExternalConfigService
import com.icthh.xm.utils.getConfigRelatedPath
import com.icthh.xm.utils.getSettings
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.log
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.VIRTUAL_FILE
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.nio.file.Files.walk
import java.nio.file.Paths


class TrackChanges() : AnAction() {

    val externalConfigService = ExternalConfigService()

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val settings = project.getSettings()?.selected()
        settings ?: return
        settings.trackChanges = true

        val basePath = project.basePath + "/config"
        walk(Paths.get(basePath)).use{
            it.filter { !it.isDirectory() }.forEach{
                settings.editedFiles.put(it.systemIndependentPath, FileState(sha256Hex(it.inputStream())))
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project

        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges?.not() ?: true

        if (settings != null) {
            addFileToTrackChanges(anActionEvent, settings, project)
        }
    }

    private fun addFileToTrackChanges(anActionEvent: AnActionEvent, settings: EnvironmentSettings ,project: Project) {
        val vFile = anActionEvent.getData(VIRTUAL_FILE)
        if (vFile == null || vFile.isDirectory || !settings.trackChanges) {
            return
        }

        val editedFiles = settings.editedFiles
        var fileState = editedFiles.get(vFile.path)
        if (fileState?.isNotified.isTrue()) {
            return
        }

        val sha256Hex = sha256Hex(vFile.inputStream)
        if (fileState == null) {
            fileState = FileState(sha256Hex)
            editedFiles.put(vFile.path, fileState)
        }

        getApplication().executeOnPooledThread {
            try {
                val path = vFile.getConfigRelatedPath(project)
                val fileContent = externalConfigService.getConfigFile(settings, path)
                if (!sha256Hex.equals(sha256Hex(fileContent))) {
                    showNotification(
                        project, "WARNING", "File ${vFile.name} difference with you local.",
                        "When you deploy config to server you will rewrite it to you version and can LOST changes from other people",
                        WARNING
                    )
                }
                fileState.isNotified = true
            } catch (e: Exception) {
                log.error("Error compare config file ", e)
                showNotification(
                    project, "ERROR", "Error get ${vFile.name} from config server",
                    e.message, ERROR
                )
            }
        }

    }

    private fun showNotification(project: Project?, dispayId: String, title: String, content: String?,
                                 notificationType: NotificationType
    ) {
        getApplication().runReadAction {
            val notification = Notification(dispayId, title, content ?: "", notificationType)
            notification.notify(project)
        }
    }
}
