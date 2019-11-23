
package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.shared.ConfirmDialog
import com.icthh.xm.service.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication

class RefreshAll() : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        val content = """
            All files (for ALL tenants) from disk will update in memory in config-ms.
             But refresh button in this plugin refresh only ONE tenant.
        """.trimIndent()
        val dialog = ConfirmDialog("Are you really want to update ALL files?", content)
        dialog.show()
        if (!dialog.isOK) {
            return
        }

        getApplication().executeOnPooledThread{
            project.allPaths().chunked(999).forEach {
                project.updateFilesInMemory(it, selected).get()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.updateSupported() ?: return
        // e.presentation.isVisible = false
    }


}
