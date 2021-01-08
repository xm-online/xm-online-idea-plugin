package com.icthh.xm.editors

import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.BrowserPipe
import com.icthh.xm.actions.JCefWebPanelWrapper
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Dimension
import javax.swing.JComponent

abstract class WebEditor(val project: Project,
                         val viewName: String,
                         val title: String,
                         val dimension: Dimension = Dimension(500, 500)): FileEditorBase() {

    val jCefWebPanelWrapper = JCefWebPanelWrapper(viewName, dimension)
    val centerPanel = lazy {
        jCefWebPanelWrapper.createCenterPanel({callbacks(it)}, {onReady(it)})
    }

    init {
        Disposer.register(this, jCefWebPanelWrapper)
    }

    override fun getComponent(): JComponent = centerPanel.value

    abstract fun callbacks(browser: JBCefBrowser): List<BrowserCallback>

    open fun onReady(pipe: BrowserPipe) {}

    fun invokeOnUiThread(operation: () -> Unit) {
        jCefWebPanelWrapper.invokeOnUiThread { operation() }
    }

    override fun getName(): String {
        return title
    }

    override fun getPreferredFocusedComponent() = jCefWebPanelWrapper.uiThreadAnchor
}
