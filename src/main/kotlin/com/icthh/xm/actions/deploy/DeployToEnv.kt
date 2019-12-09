package com.icthh.xm.actions.deploy

import com.icthh.xm.service.getChangedFiles
import com.icthh.xm.service.getSettings
import com.icthh.xm.service.updateFilesInMemory
import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.isTrue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager


class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        val changesFiles = project.getChangedFiles()

        val fileListDialog = FileListDialog(project, changesFiles)
        fileListDialog.show()
        if (fileListDialog.isOK) {
            FileDocumentManager.getInstance().saveAllDocuments()
            changesFiles.refresh(project)
            project.updateFilesInMemory(changesFiles, selected)
        }

    }


    override fun update(anActionEvent: AnActionEvent) {
        super.update(anActionEvent)
        if (anActionEvent.updateSupported() == null) {
            return
        }
        val project = anActionEvent.project ?: return
        val selected = project.getSettings().selected()
        anActionEvent.presentation.isEnabled = selected?.trackChanges.isTrue() || (selected?.startTrackChangesOnEdit.isTrue())
    }
}
