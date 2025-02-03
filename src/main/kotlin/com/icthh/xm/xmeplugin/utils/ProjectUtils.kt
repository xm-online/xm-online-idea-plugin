package com.icthh.xm.xmeplugin.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.services.settings.SettingService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import doUpdateSymlinkToLep
import readTextAndClose
import java.io.File
import java.util.*


const val CONFIG_DIR_NAME = "/config"

fun Project.getSettings() = this.service<SettingService>()

fun Project?.isConfigProject(): Boolean {
    return this != null && isConfigRoot(this.basePath)
}

private fun toConfigFolder(basePath: String?) = basePath + CONFIG_DIR_NAME

fun isConfigRoot(path: String?): Boolean {
    return File(toConfigFolder(path)).exists() && File(toConfigFolder(path) + "/tenants/tenants-list.json").exists()
}

fun Project.getConfigRootDir() = if (isConfigProject()) {
    toConfigFolder(basePath)
} else {
    toConfigFolder(getSettings().selected()?.basePath)
}

fun Project.getLinkedConfigRootDir() = if (isConfigProject()) {
    getConfigRootDir()
} else {
    "${basePath}/src/main/lep"
}

fun Project.root() =
    VfsUtil.findFile(File(this.getConfigRootDir()).toPath(), true)?.parent

fun Project?.isSupportProject() = this != null && (isConfigProject() || isXmeMicroservice())
fun AnActionEvent.updateSupported(): Boolean? {
    presentation.isVisible = project?.isSupportProject() ?: false
    return if (presentation.isVisible) true else null
}


val PROJECT_CONFIG = Key<ProjectConfig>("XME_PROJECT_CONFIG")
data class Bootstrap(val spring: SpringConfig?)
data class SpringConfig(val application: ApplicationConfig?)
data class ApplicationConfig(val name: String?)

data class ProjectConfig(val name: String?)

fun Project?.isXmeMicroservice(): Boolean {
    return this?.getApplicationName() != null
}

fun Project.getApplicationName(): String? {
    val projectConfig = this.getUserData(PROJECT_CONFIG)
    if (projectConfig != null) {
        return projectConfig.name
    }

    val appName = loadBootStrapYml(basePath)
    if (appName != null && usedXmCommons(basePath)) {
        return appName
    }

    val gradleProperties = VfsUtil.findFile(File("${this.basePath}/gradle.properties").toPath(), true) ?: return null
    val properties = Properties()
    properties.load(gradleProperties.inputStream)
    val coreUrl = properties.getProperty("xm_git_url")
    if (coreUrl != null) {
        val name = coreUrl.split("/").last().replace(".git", "")
        if (usedXmCommons("$basePath/$name")) {
            return loadBootStrapYml("$basePath/$name")
        }
    }

    this.putUserData(PROJECT_CONFIG, ProjectConfig(null))
    return null
}

private fun usedXmCommons(baseUrl: String?): Boolean {
    val gradleFile = VfsUtil.findFile(File("$baseUrl/build.gradle").toPath(), true)
    val deps = gradleFile?.inputStream?.readTextAndClose()
    return deps?.contains("com.icthh.xm.commons:xm-commons") ?: false
}

private fun Project.loadBootStrapYml(baseUrl: String?): String? {
    val configFile = VfsUtil.findFile(File("$baseUrl/src/main/resources/config/bootstrap.yml").toPath(), true)
    val config: Bootstrap? = configFile?.let { YAML_MAPPER.readValue<Bootstrap>(it.contentsToByteArray()) }
    if (configFile != null) {
        this.putUserData(PROJECT_CONFIG, ProjectConfig(config?.spring?.application?.name))
        return config?.spring?.application?.name
    }
    return null
}

fun Project.showInfoNotification(title:String, message: () -> String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XME.digital")
        .createNotification(
            title = title,
            content = message.invoke(),
            type = NotificationType.INFORMATION
        )
        .notify(this)
}

fun Project.showInfoNotification(title:String, message: String, actions: Map<String, (AnActionEvent, Notification) -> Unit>) {
    val notification: Notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("XME.digital")
        .createNotification(title, message, NotificationType.INFORMATION)
    actions.forEach({ (key, value) ->
        notification.addAction(object: NotificationAction(key) {
            override fun actionPerformed(action: AnActionEvent, notification: Notification) {
                value.invoke(action, notification)
            }
        })
    })
    notification.notify(this)
}

fun Project.showErrorNotification(title:String, message: () -> String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XME.digital")
        .createNotification(
            title = title,
            content = message.invoke(),
            type = NotificationType.ERROR
        )
        .notify(this)
}

fun Project.getTenants(root: String? = null): List<String> {
    var path = root ?: this.getSettings().selected()?.basePath
    if (isConfigProject()) {
        path = this.basePath
    }
    path ?: return emptyList()
    val tenants = ArrayList<String>()
    if (path.isNotBlank()) {
        val tenantsPath = "${path}/config/tenants"
        val tenantsDirectory = File(tenantsPath)
        if (tenantsDirectory.exists()) {
            val tenantFolders = tenantsDirectory.list() ?: emptyArray()
            val tenantsList = tenantFolders.filter { File("${tenantsPath}/${it}").isDirectory }
            tenants.addAll(tenantsList)
        }
    }
    return tenants
}

class Feature(): Comparable<Feature> {
    var name: String = "";
    var version: String = "";

    constructor(name: String, version: String): this() {
        this.name = name
        this.version = version
    }

    override fun compareTo(other: Feature): Int {
        return "$name/$version".compareTo("${other.name}/${other.version}")
    }
}
fun Project.getFeatures(root: String? = null): List<Feature> {
    var path = root ?: this.getSettings().selected()?.basePath
    if (isConfigProject()) {
        path = this.basePath
    }
    path ?: return emptyList()
    if (path.isBlank()) {
        return emptyList()
    }
    val featuresPath = "${path}/config/features"
    val featuresDirectory = File(featuresPath)
    if (featuresDirectory.exists()) {
        val featuresFolders = featuresDirectory.list() ?: emptyArray()
        val featuresList = featuresFolders.map { File("${featuresPath}/${it}") }.filter { it.isDirectory }
        return featuresList.flatMap { feature ->
            val versionFolders = feature.list() ?: emptyArray()
            versionFolders.map { File("${featuresPath}/${feature.name}/${it}") }
                .filter { it.isDirectory }
                .map { Feature(feature.name, it.name) }
        }
    }

    return emptyList()
}

fun Project?.projectType(): String {
    if (isConfigProject()) {
        return "CONFIG"
    } else if (isXmeMicroservice()) {
        return "MICROSERVICE"
    } else {
        return "UNKNOWN"
    }
}

fun Project.updateEnv() {
    doAsync{
        doUpdateSymlinkToLep()
        try {
            runReadAction {
                this.getTenants().forEach {
                    // TODO this.xmEntitySpecService.computeTenantEntityInfo(it)
                }
            }
        } catch (e: Throwable) {
            log.error("Error {}", e)
            throw e
        }
    }
}
