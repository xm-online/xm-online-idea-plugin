package com.icthh.xm.actions.settings

import com.fasterxml.jackson.databind.ObjectMapper
import com.icthh.xm.units.getSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javafx.application.Platform
import javafx.application.Platform.runLater
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import java.awt.Dimension
import javax.swing.JComponent


class SettingsDialog(val project: Project): DialogWrapper(project) {

    private val objectMapper = ObjectMapper()
    private var settingService: SettingService = project.getSettings()

    init {
        init()
        title = "Settings dialog"
    }

    override fun createCenterPanel(): JComponent? {
        val fxPanel = JFXPanel()
        fxPanel.preferredSize = Dimension(500, 500)

        Platform.setImplicitExit(false)



        runLater {
            val root = VBox()
            val scene = Scene(root)

            val webView = WebView()
            //webView.engine.load(
                //javaClass.getResource("/settings.html").toExternalForm()
            //)
            webView.engine.load("http://localhost:8080")
            root.setMinSize(500.0, 500.0)
            root.getChildren().add(webView)
            fxPanel.scene = scene

            val window = webView.engine.executeScript("window") as JSObject
            window.setMember("app", JSBridge(settingService) {
                val data = objectMapper.readValue(it, SettingService::class.java)
                settingService.envs = data.envs
            })
        }

        return fxPanel
    }

}
