package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.shared.ConfirmDialog
import com.icthh.xm.service.*
import com.icthh.xm.service.filechanges.UncorrectStateOfRepository
import com.icthh.xm.utils.isTrue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager


class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        FileDocumentManager.getInstance().saveAllDocuments()


        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val changesFiles = project.getChangedFiles()

                ApplicationManager.getApplication().invokeLater {
                    val fileListDialog = WebFileListDialog(project, changesFiles)
                    fileListDialog.show()
                    if (fileListDialog.isOK) {
                        FileDocumentManager.getInstance().saveAllDocuments()
                        changesFiles.refresh(project)
                        project.updateFilesInMemory(changesFiles, selected)
                    }
                }
            } catch (e: UncorrectStateOfRepository) {
                ApplicationManager.getApplication().invokeLater {
                    val content = """
            Uncorrect state of repository ${project.getRepository(true)?.state?.name}
        """.trimIndent()
                    val dialog = ConfirmDialog("Uncorrect state for repository", content)
                    dialog.show()
                }
            }
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
