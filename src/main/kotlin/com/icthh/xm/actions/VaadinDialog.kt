package com.icthh.xm.actions

import com.icthh.xm.ViewRegistry.registry
import com.icthh.xm.ViewRegistry.unregistry
import com.icthh.xm.serverPort
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.vaadin.navigator.View
import javafx.application.Platform.runLater
import javafx.application.Platform.setImplicitExit
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import java.awt.Dimension
import javax.swing.JComponent

abstract class VaadinDialog(val project: Project,
                            val viewName: String,
                            val dimension: Dimension = Dimension(500, 500),
                            dialogTitle: String = "Dialog"): DialogWrapper(project) {

    init {
        init()
        title = dialogTitle
    }

    override fun createCenterPanel(): JComponent? {
        registry(viewName, view())

        val fxPanel = JFXPanel()
        fxPanel.preferredSize = dimension
        setImplicitExit(false)
        runLater {
            val root = VBox()
            val scene = Scene(root)
            val webView = WebView()
            webView.engine.load("http://localhost:$serverPort/#!$viewName")
            root.setMinSize(dimension.getWidth(), dimension.getHeight())
            root.getChildren().add(webView)
            fxPanel.scene = scene
        }
        return fxPanel
    }

    abstract fun view() : View

    override fun dispose() {
        super.dispose()
        unregistry(viewName)
    }

}
