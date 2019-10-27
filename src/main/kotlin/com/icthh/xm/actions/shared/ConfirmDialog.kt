package com.icthh.xm.actions.shared;

import com.intellij.openapi.ui.DialogWrapper
import icons.PluginIcons.WARNING
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class ConfirmDialog(val dialogTitle: String,
                    val caption: String,
                    val icon: Icon = WARNING) : DialogWrapper(true) {
        init {
                init()
                setTitle(dialogTitle)
        }

        override fun createCenterPanel(): JComponent?{
                val dialogPanel = JPanel(BorderLayout())

                val label = JLabel("    " + caption)
                label.preferredSize = Dimension(150,40)
                dialogPanel.add(label,BorderLayout.CENTER)
                dialogPanel.add(JLabel(icon), BorderLayout.WEST)

                return dialogPanel
        }

}
