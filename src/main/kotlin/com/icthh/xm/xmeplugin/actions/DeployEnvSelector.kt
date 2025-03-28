package com.icthh.xm.xmeplugin.actions

import com.icthh.xm.xmeplugin.services.settings.EnvironmentSettings
import com.icthh.xm.xmeplugin.services.settings.NULL_ENV
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Dimension
import javax.swing.JComponent


class DeployEnvSelector : ComboBoxAction(), DumbAware {
    private val envs: MutableList<EnvironmentSettings> = ArrayList()
    private var selectedItem: EnvironmentSettings? = null
    private var component: ComboBoxButton? = null

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.updateSupported() ?: return

        val presentation = e.presentation
        envs.clear()
        val elements = e.project?.getSettings()?.envs ?: emptyList()
        envs.addAll(elements)
        component?.updateUI()
        selectedItem = e.project?.getSettings()?.selected()
        presentation.text = selectedItem?.name ?: "No env"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        log.info("actionPerformed=>> ${e.project?.name}")
    }

    override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
        val component = super.createComboBoxButton(presentation)
        this.component = component;
        return component
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val createCustomComponent = super.createCustomComponent(presentation, place)
        val placeholder = "Select deploy environment"
        createCustomComponent.toolTipText = placeholder
        val dimension = Dimension(150, 30)
        createCustomComponent.preferredSize = dimension
        return createCustomComponent
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return group
        for (env in project.getSettings().envs) {
            group.add(EnvironmentSettingsAction(env))
        }
        if (!project.isConfigProject()) {
            group.add(EnvironmentSettingsAction(NULL_ENV))
        }
        return group
    }

    private inner class EnvironmentSettingsAction constructor(val environmentSettings: EnvironmentSettings) : DumbAwareAction() {

        override fun actionPerformed(e: AnActionEvent) {
            selectedItem = environmentSettings
            e.project?.getSettings()?.select(environmentSettings)
            e.project?.doAsync {
                e.project?.updateEnv()
            }
        }

        init {
            templatePresentation.setText(environmentSettings.name)
        }
    }

}

