package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.FileState
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.*
import com.icthh.xm.utils.*
import com.intellij.history.LocalHistory
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.VIRTUAL_FILE
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.lang.System.currentTimeMillis
import java.nio.file.Files.walk
import java.nio.file.Paths


class TrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        FileDocumentManager.getInstance().saveAllDocuments()
        project.startTrackChanges()
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return

        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges?.not() ?: true

        if (settings != null) {
            addFileToTrackChanges(anActionEvent, settings, project)
        }
    }

    private fun addFileToTrackChanges(anActionEvent: AnActionEvent, settings: EnvironmentSettings, project: Project) {
        val externalConfigService = project.getExternalConfigService()
        val vFile = anActionEvent.getData(VIRTUAL_FILE)
        if (vFile == null || vFile.isDirectory || !settings.trackChanges || !vFile.isConfigFile(project)) {
            return
        }

        val editedFiles = settings.editedFiles
        var fileState = editedFiles.get(vFile.path)
        if (fileState?.isNotified.isTrue() || settings.wasNotifiedAtLastTime()) {
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
                val fileContent = externalConfigService.getConfigFile(project, settings, path)
                if (!sha256Hex.equals(sha256Hex(fileContent))) {
                    if (fileState.isNotified.isTrue() || settings.wasNotifiedAtLastTime()) {
                        return@executeOnPooledThread
                    }
                    project.showNotification("WARNING", "File ${vFile.name} difference with you local.", WARNING) {
                        "When you deploy config to server you will rewrite it to you version and can LOST changes from other people"
                    }
                }
                fileState.isNotified = true
            } catch (nf: NotFoundException) {
                log.info("File ${vFile.name} not found on config server")
                fileState.isNotified = true
            } catch (e: Exception) {
                settings.lastTimeTryToNotifyAboutDifference = currentTimeMillis()
                log.warn("Error compare config file ", e)
                project.showNotification("ERROR", "Error get ${vFile.name} from config server", ERROR) {
                    "${(e.message ?: "")} ${settings.lastTimeTryToNotifyAboutDifference}"
                }
            } finally {
                getApplication().invokeLater {
                    getApplication().runWriteAction {
                        project.save()
                    }
                }
            }
        }
    }

    private fun EnvironmentSettings.wasNotifiedAtLastTime(): Boolean {
      return currentTimeMillis() - lastTimeTryToNotifyAboutDifference < 3600_000
    }

}
