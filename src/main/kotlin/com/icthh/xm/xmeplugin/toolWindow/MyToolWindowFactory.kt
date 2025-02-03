package com.icthh.xm.xmeplugin.toolWindow

import com.icthh.xm.xmeplugin.utils.isSupportProject
import com.icthh.xm.xmeplugin.webview.WebDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.sun.java.accessibility.util.AWTEventMonitor.addActionListener
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = project.isSupportProject()

    class MyToolWindow(val toolWindow: ToolWindow) {
        fun getContent() = JBPanel<JBPanel<*>>().apply {
            add(JButton("Test button").apply {
                addActionListener {

                }
            })
            add(JButton("Run action").apply {
                addActionListener {

                }
            })
        }
    }

}
