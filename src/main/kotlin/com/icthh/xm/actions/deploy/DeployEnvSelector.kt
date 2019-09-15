package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class DeployEnvSelector() : ComboBoxAction() {

    lateinit var project: Project

    override fun createPopupActionGroup(button: JComponent): DefaultActionGroup {
        val actions = project.getSettings().envs.map {object: AnAction(it.name) {
            override fun actionPerformed(e: AnActionEvent) {
                project.getSettings().selectedEnv = it.id
                updateSelectName()
            }
        }}
        val actionGroup = DefaultActionGroup(actions)
        return actionGroup
    }

    override fun actionPerformed(anActionEvent: AnActionEvent) {

    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
        if (project != null) {
            this.project = project
        } else {
            return
        }
        updateSelectName()
    }

    private fun updateSelectName() {
        val settings = project.getSettings()
        val name = settings.selected()?.name
        templatePresentation.text = name ?: "Select deploy env"
    }

}
