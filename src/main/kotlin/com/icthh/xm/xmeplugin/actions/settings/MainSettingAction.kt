package com.icthh.xm.xmeplugin.actions.settings

import com.icthh.xm.xmeplugin.utils.getSettings
import com.icthh.xm.xmeplugin.utils.log
import com.icthh.xm.xmeplugin.utils.updateEnv
import com.icthh.xm.xmeplugin.utils.updateSupported
import com.icthh.xm.xmeplugin.webview.SettingsDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


class MainSettingAction : AnAction() {

    init {
        log.info("Init main settings")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = project.getSettings()
        val basePath = settings.selected()?.basePath
        val listOfTenants = HashSet<String>(settings.selected()?.selectedTenants ?: listOf())

        val dialog = SettingsDialog(project)
        dialog.show()
        if (dialog.isOK) {
            settings.envs.clear()
            settings.envs.addAll(dialog.data)
            if (basePath != settings.selected()?.basePath
                || !listOfTenants.equals(settings.selected()?.selectedTenants)) {
                project.updateEnv()
            }
        }
        project.save()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        e.updateSupported() ?: return
    }
}
