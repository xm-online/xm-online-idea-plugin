package com.icthh.xm.actions.deploy

import com.icthh.xm.service.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.fileEditor.FileDocumentManager

class DeployCurrentFile: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return
        val file = e.getDataContext().getData(VIRTUAL_FILE)
        if (file == null || !file.isConfigFile(project)) {
            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()

        val changes = project.getChangedFiles(setOf(project.toAbsolutePath(file.path)), true)

        val fileListDialog = WebFileListDialog(project, changes)
        fileListDialog.show()
        if (fileListDialog.isOK) {
            FileDocumentManager.getInstance().saveAllDocuments()
            changes.refresh(project)
            project.updateFilesInMemory(changes, selected)
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
    }
}
