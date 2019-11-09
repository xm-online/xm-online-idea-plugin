package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.icthh.xm.utils.updateSupported
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {

    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project ?: return

        anActionEvent.presentation.isEnabled = project.getSettings().selected()?.trackChanges ?: false
    }
}
