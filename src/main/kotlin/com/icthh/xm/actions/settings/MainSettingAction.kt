package com.icthh.xm.actions.settings

import com.icthh.xm.service.getSettings
import com.icthh.xm.service.isSupportProject
import com.icthh.xm.service.updateEnv
import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MainSettingAction : AnAction() {

    init {
        logger.info("Init main settings")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // e.presentation.isEnabled = true
        val basePath = project.getSettings().selected()?.basePath
        val listOfTenants = HashSet<String>(project.getSettings().selected()?.selectedTenants ?: listOf())

        val dialog = SettingsDialog(project)
        dialog.show()
        if (dialog.isOK) {
            project.getSettings().envs.clear()
            project.getSettings().envs.addAll(dialog.data)
            if (basePath != project.getSettings().selected()?.basePath
                || !listOfTenants.equals(project.getSettings().selected()?.selectedTenants)) {
                project.updateEnv()
            }
        }
        project.save()
    }

    override fun update(e: AnActionEvent) {
        // e.presentation.isEnabled = true
        e.updateSupported() ?: return
    }
}
