package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.utils.getSettings
import com.icthh.xm.utils.log
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.plaf.basic.BasicComboBoxRenderer


class DeployEnvSelector() : AnAction(), CustomComponentAction {

    var project: Project? = null
    val comboBox = ComboBox<EnvironmentSettings>()
    var envs: MutableList<EnvironmentSettings> = ArrayList()

    override fun createCustomComponent(presentation: Presentation): JComponent {
        comboBox.addItemListener {
            project?.getSettings()?.select(it.item as EnvironmentSettings?)
        }
        val placeholder = "Select deploy environment"
        setPlaceholder(placeholder)
        comboBox.toolTipText = placeholder
        val dimension = Dimension(150, 30)
        //comboBox.maximumSize = dimension
        comboBox.preferredSize = dimension
        //comboBox.size = dimension
        //comboBox.minimumSize = dimension
        return comboBox
    }

    private fun setPlaceholder(placeholder: String) {
        val renderer = comboBox.renderer
        comboBox.renderer = object : ListCellRenderer<EnvironmentSettings> {
            override fun getListCellRendererComponent(
                list: JList<out EnvironmentSettings>?,
                value: EnvironmentSettings?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = JLabel()
                if (index == -1 && value == null) {
                    label.setText(placeholder)
                    return label
                }

                val jComponent = renderer.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                ) as JComponent
                jComponent.toolTipText = value.toString()
                return jComponent
            }
        }
    }

    override fun actionPerformed(event: AnActionEvent) {

    }

    override fun update(anActionEvent: AnActionEvent) {
        log.info("update: ${anActionEvent}")
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
        if (project != null) {
            this.project = project
        } else {
            return
        }

        val envs = project.getSettings().envs
        if (this.envs.equals(envs)) {
            comboBox.updateUI()
            return
        }
        this.envs.clear()
        this.envs.addAll(envs);
        val selected = project.getSettings().selected()
        comboBox.removeAllItems()
        envs.forEach { comboBox.addItem(it) }
        comboBox.selectedItem = selected
        comboBox.updateUI()
    }

}
