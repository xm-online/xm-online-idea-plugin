package com.icthh.xm.actions.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.BrowserPipe
import com.icthh.xm.actions.WebDialog
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.*
import com.icthh.xm.utils.logger
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Dimension


class SettingsDialog(project: Project): WebDialog(
    project, "settings", Dimension(820, 600), "Settings"
) {

    val mapper = jacksonObjectMapper()
    var data = ArrayList<EnvironmentSettings>()
    val updateModes = UpdateMode.values().toList().map { UpdateModeDto(it.isGitMode, it.name) }

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                logger.info("Update ${body}")

                val data = ArrayList(project.getSettings().envs.map { it.copy() })
                this.data.clear()
                this.data.addAll(data)
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "updateModes" to updateModes,
                    "branches" to project.getRepository()?.getLocalBranches(),
                    "envs" to data,
                    "isConfigProject" to project.isConfigProject()
                )))
            },
            BrowserCallback("envsUpdated") {body, pipe ->
                logger.info("envsUpdated ${body}")
                val envs = mapper.readValue<List<EnvironmentSettings>>(body)
                this.data.clear()
                this.data.addAll(envs)
            },
            BrowserCallback("testConnection") {body, pipe ->
                try {
                    val env = mapper.readValue<EnvironmentSettings>(body)
                    project.getExternalConfigService().fetchToken(env)
                    pipe.post("connectionResult", mapper.writeValueAsString(mapOf(
                        "success" to true,
                    )))
                } catch (e: Exception) {
                    pipe.post("connectionResult", mapper.writeValueAsString(mapOf(
                        "success" to false,
                    )))
                    project.showNotification("TestAuth", "Error test authentication", NotificationType.ERROR) {
                        e.message ?: ""
                    }
                }
            }
        )
    }
}

data class UpdateModeDto(
    val isGitMode: Boolean,
    val name: String
)

