package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.service.getSettings
import com.icthh.xm.utils.log
import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.logger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.PopupMenuListenerAdapter
import org.jetbrains.annotations.Nullable
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener


class DeployEnvSelector() : AnAction(), CustomComponentAction {

    var project: Project? = null
    val comboBoxes = HashMap<String, ComboBox<EnvironmentSettings>>()
    var envs: MutableList<EnvironmentSettings> = ArrayList()


    override fun createCustomComponent(presentation: Presentation): JComponent {
        val comboBox = ComboBox<EnvironmentSettings>()
        val project = this.project
        comboBox.addItemListener {
            this.project?.getSettings()?.select(comboBox.selectedItem as EnvironmentSettings?)
        }
        val placeholder = "Select deploy environment"
        setPlaceholder(comboBox, placeholder)
        comboBox.toolTipText = placeholder
        val dimension = Dimension(150, 30)
        //comboBox.maximumSize = dimension
        comboBox.preferredSize = dimension
        //comboBox.size = dimension
        //comboBox.minimumSize = dimension
        if (project != null) {
            comboBoxes.put(project.locationHash, comboBox)
        }
        comboBox.addPopupMenuListener(object: PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                super.popupMenuWillBecomeVisible(e)
                logger.info("opened env selector ${project?.getSettings()?.envs} ${comboBox.itemCount}")
                comboBox.updateUI()
                if (project != null) {
                    refreshItems(project)
                }
            }
        })

        return comboBox
    }

    private fun setPlaceholder(comboBox: ComboBox<EnvironmentSettings>, placeholder: String) {
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
        anActionEvent.updateSupported() ?: return

        //logger.info("update: ${anActionEvent}")
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
        if (project != null) {
            this.project = project
        } else {
            return
        }

        refreshItems(project)
    }

    private fun refreshItems(project: Project) {
        val comboBox = comboBoxes.get(project.locationHash) ?: return

        val envs = project.getSettings().envs
        if (this.envs.equals(envs) && comboBox.itemCount == envs.size) {
            comboBox.updateUI()
            return
        }

        logger.info("${project} >>> envs not the same ${envs} <<<>>> ${this.envs} | ${comboBox.itemCount} != ${envs.size}")

        this.envs.clear()
        this.envs.addAll(envs)
        val selected = project.getSettings().selected()
        comboBox.removeAllItems()
        envs.forEach { comboBox.addItem(it) }
        comboBox.selectedItem = selected
        comboBox.updateUI()
    }

}
