package com.icthh.xm.xmeplugin.webview

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.services.configRestService
import com.icthh.xm.xmeplugin.services.settings.EnvironmentSettings
import com.icthh.xm.xmeplugin.services.settings.UpdateMode
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.jcef.JBCefBrowser
import getLocalBranches
import normalizedPath
import java.awt.Dimension
import java.io.File


class SettingsDialog(project: Project): WebDialog(
    project, "settings", Dimension(820, 650), "Settings"
) {

    val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    var data = ArrayList<EnvironmentSettings>()
    val updateModes = UpdateMode.entries.map { UpdateModeDto(true, it.name) }

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                log.info("Update ${body}")

                val data = ArrayList(project.getSettings().envs.map { it.copy() })
                this.data.clear()
                this.data.addAll(data)
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "tenants" to project.getTenants(),
                    "updateModes" to updateModes,
                    "branches" to project.getLocalBranches(),
                    "envs" to data,
                    "isConfigProject" to project.isConfigProject(),
                    "projectType" to project.projectType()
                )))
            },
            BrowserCallback("getTenants") {body, pipe ->
                log.info("getTenants ${body}")
                val basePathHolder = mapper.readValue<BasePathHolder>(body)
                pipe.post("setTenants", mapper.writeValueAsString(mapOf(
                    "tenants" to project.getTenants(basePathHolder.basePath),
                    "features" to project.getFeatures(basePathHolder.basePath),
                )))
            },
            BrowserCallback("envsUpdated") {body, pipe ->
                log.info("envsUpdated ${body}")
                val envs = mapper.readValue<List<EnvironmentSettings>>(body)
                this.data.clear()
                this.data.addAll(envs)
            },
            BrowserCallback("testConnection") {body, pipe ->
                try {
                    val env = mapper.readValue<EnvironmentSettings>(body)
                    project.configRestService.fetchToken(env)
                    pipe.post("connectionResult", mapper.writeValueAsString(mapOf(
                        "success" to true,
                    )))
                } catch (e: Exception) {
                    pipe.post("connectionResult", mapper.writeValueAsString(mapOf(
                        "success" to false,
                    )))
                    project.showErrorNotification("Error test authentication") {
                        e.message ?: ""
                    }
                }
            },
            BrowserCallback("openFileInput") {body, pipe ->
                val item = mapper.readValue<CurrentPath>(body)
                val currentFile = item.currentPath?.let { VfsUtil.findFile(File(it).toPath(), false) }
                invokeOnUiThread {
                    FileChooser.chooseFile(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                        project,
                        currentFile
                    ) {
                        var path: String? = it.path
                        log.info("Selected path: ${path}")
                        var configDir: File? = File(path!!)
                        while (configDir !=null && configDir.exists() && !isConfigRoot(configDir.normalizedPath)) {
                            configDir = configDir.parentFile
                        }
                        path = configDir?.normalizedPath

                        if (path == null) {
                            pipe.post("fileSelected", mapper.writeValueAsString(mapOf(
                                "path" to it.path,
                                "isConfigRoot" to false,
                                "id" to item.id
                            )))
                            project.showErrorNotification("Wrong path") {
                                "${it.path} it's not config root."
                            }
                            return@chooseFile
                        }

                        pipe.post("fileSelected", mapper.writeValueAsString(mapOf(
                            "path" to path,
                            "isConfigRoot" to true,
                            "id" to item.id
                        )))
                    }
                }
            },
        )
    }
}

data class CurrentPath (
    val currentPath: String?,
    val id: String?
)

data class UpdateModeDto(
    val isGitMode: Boolean,
    val name: String
)

data class BasePathHolder (
    val basePath: String?
)

