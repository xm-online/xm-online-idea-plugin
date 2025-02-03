package com.icthh.xm.xmeplugin.actions


import ConflictStateOfRepository
import calculateChangedFilesState
import com.icthh.xm.xmeplugin.services.configRestService
import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.webview.WebFileListDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.fileEditor.FileDocumentManager
import isConfigFile
import toAbsolutePath
import toRelatedPath

class DeployCurrentFile: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return
        val file = e.dataContext.getData(VIRTUAL_FILE)
        if (file == null || !file.isConfigFile(project)) {
            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        project.doAsync {
            try {
                val changes = project.calculateChangedFilesState(setOf(project.toRelatedPath(project.toAbsolutePath(file.path))), true)
                invokeLater {
                    val fileListDialog = WebFileListDialog(project, changes)
                    fileListDialog.show()
                    if (fileListDialog.isOK) {
                        FileDocumentManager.getInstance().saveAllDocuments()
                        project.doAsync {
                            changes.refresh(project)
                            project.configRestService.updateFilesInMemory(project, changes, selected)
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

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
