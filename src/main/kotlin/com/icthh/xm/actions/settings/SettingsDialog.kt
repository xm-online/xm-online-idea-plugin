package com.icthh.xm.actions.settings

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.WebDialog
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.*
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.logger
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Dimension
import java.io.File


class SettingsDialog(project: Project): WebDialog(
    project, "settings", Dimension(820, 600), "Settings"
) {

    val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    var data = ArrayList<EnvironmentSettings>()
    val updateModes = if (project.isConfigProject()) {
        UpdateMode.values().toList().map { UpdateModeDto(it.isGitMode, it.name) }
    } else {
        UpdateMode.values().toList().map { UpdateModeDto(it.isGitMode, it.name) }.filter { it.isGitMode }
    }

    private fun getTenants(basePath: String?): List<String> {
        val tenants = ArrayList<String>()
        if (!basePath.isNullOrBlank()) {
            val tenantsPath = "${basePath}/config/tenants"
            val tenantsDirectory = File(tenantsPath)
            if (tenantsDirectory.exists()) {
                val tenantFolders = tenantsDirectory.list() ?: emptyArray()
                val tenantsList = tenantFolders.filter { File("${tenantsPath}/${it}").isDirectory }
                tenants.addAll(tenantsList)
            }
        }
        return tenants
    }

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                logger.info("Update ${body}")

                val data = ArrayList(project.getSettings().envs.map { it.copy() })
                this.data.clear()
                this.data.addAll(data)
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "tenants" to getTenants(project.getSettings()?.selected()?.basePath),
                    "updateModes" to updateModes,
                    "branches" to project.getRepository()?.getLocalBranches(),
                    "envs" to data,
                    "isConfigProject" to project.isConfigProject(),
                    "projectType" to project.projectType()
                )))
            },
            BrowserCallback("getTenants") {body, pipe ->
                logger.info("getTenants ${body}")
                val basePathHolder = mapper.readValue<BasePathHolder>(body)
                pipe.post("setTenants", mapper.writeValueAsString(mapOf(
                    "tenants" to getTenants(basePathHolder.basePath),
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
                        logger.info("Selected path: ${path}")
                        var configDir: File? = File(path!!)
                        while (configDir !=null && configDir.exists() && !isConfigRoot(configDir.absolutePath)) {
                            configDir = configDir.parentFile
                        }
                        path = configDir?.absolutePath

                        if (path == null) {
                            pipe.post("fileSelected", mapper.writeValueAsString(mapOf(
                                "path" to it.path,
                                "isConfigRoot" to false,
                                "id" to item.id
                            )))
                            project.showNotification("ConfigRoot", "Wrong path", NotificationType.ERROR) {
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

