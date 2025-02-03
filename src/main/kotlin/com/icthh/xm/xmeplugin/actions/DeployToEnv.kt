package com.icthh.xm.xmeplugin.actions

import ConflictStateOfRepository
import calculateChangedFilesState
import com.icthh.xm.xmeplugin.services.ErrorUpdateConfiguration
import com.icthh.xm.xmeplugin.services.NotFoundException
import com.icthh.xm.xmeplugin.services.configRestService
import com.icthh.xm.xmeplugin.services.settings.NULL_ENV
import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.webview.WebFileListDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project


class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        saveDocuments()

        project.doAsync {
            try {
                val changesFiles = project.calculateChangedFilesState()

                invokeLater {
                    val fileListDialog = WebFileListDialog(project, changesFiles)
                    fileListDialog.show()
                    if (fileListDialog.isOK) {
                        saveDocuments()
                        project.doAsync {
                            changesFiles.refresh(project)
                            project.configRestService.updateFilesInMemory(project, changesFiles, selected)
                        }
                    }
                }
            } catch (e: ConflictStateOfRepository) {
                invokeLater {
                    project.showErrorNotification("Conflict state for repository") {
                        "Resolve merge conflict before deploy in memory"
                    }
                }
            }
        }
    }

    private fun saveDocuments() {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(anActionEvent: AnActionEvent) {
        super.update(anActionEvent)
        if (anActionEvent.updateSupported() == null) {
            return
        }
        val project = anActionEvent.project ?: return
        val selected = project.getSettings().selected()
        anActionEvent.presentation.isEnabled = selected != null && selected.name != NULL_ENV.name
    }
}
