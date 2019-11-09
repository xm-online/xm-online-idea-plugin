package com.icthh.xm.actions.settings

import com.icthh.xm.utils.getSettings
import com.icthh.xm.utils.updateSupported
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MainSettingAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = SettingsDialog(project)
        dialog.show()
        if (dialog.isOK) {
            project.getSettings().envs.clear()
            project.getSettings().envs.addAll(dialog.data)
        }
    }

    override fun update(e: AnActionEvent) {
        e.updateSupported() ?: return
        e.presentation.isEnabled = e.project != null
    }
}
