package com.icthh.xm.xmeplugin.toolWindow

import com.icthh.xm.xmeplugin.utils.invokeLater
import com.icthh.xm.xmeplugin.utils.isSupportProject
import com.icthh.xm.xmeplugin.utils.showErrorNotification
import com.icthh.xm.xmeplugin.utils.showInfoNotification
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import javax.swing.JButton


class XmePluginToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = project.isSupportProject()

    class MyToolWindow(val toolWindow: ToolWindow) {
        fun getContent() = JBPanel<JBPanel<*>>().apply {
            add(JButton("Refresh plugin configuration").apply {
                addActionListener {
                    invokeLater {
                        try {
                            toolWindow.project.xmePluginSpecService.updateCustomConfig() {
                                toolWindow.project.showInfoNotification("Plugin configuration updated") {
                                    "Plugin configuration updated successfully"
                                }
                            }
                        } catch (e: Exception) {
                            toolWindow.project.showErrorNotification("Error on update plugin configuration") {
                                e.toString()
                            }
                        }
                    }
                }
            })
        }
    }

}
