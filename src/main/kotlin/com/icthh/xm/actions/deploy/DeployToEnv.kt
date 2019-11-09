package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

import javax.swing.*

class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {

    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project ?: return
        anActionEvent.presentation.isEnabled = project.getSettings().selected()?.trackChanges ?: false
    }
}
