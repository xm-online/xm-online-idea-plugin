package com.icthh.xm.actions.deploy

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

import javax.swing.*

class DeployToEnv : AnAction {

    constructor() : super() {}

    constructor(menuText: String?, menuDescription: String?, menuIcon: Icon?) : super(
        menuText,
        menuDescription,
        menuIcon
    ) {}

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        // Using the event, create and show a dialog
        val currentProject = anActionEvent.project
        val dlgMsg = StringBuffer(anActionEvent.presentation.text + " Selected!")
        val dlgTitle = anActionEvent.presentation.description
        // If an element is selected in the editor, add info about it.
        val nav = anActionEvent.getData(CommonDataKeys.NAVIGATABLE)
        if (nav != null) {
            dlgMsg.append(String.format("\nSelected Element: %s", nav.toString()))
        }
        Messages.showMessageDialog(currentProject, dlgMsg.toString(), dlgTitle, Messages.getInformationIcon())
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabledAndVisible = project != null
    }
}
