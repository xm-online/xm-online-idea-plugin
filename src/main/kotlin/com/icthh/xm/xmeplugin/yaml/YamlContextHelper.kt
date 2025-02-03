package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.utils.YamlNode
import com.icthh.xm.xmeplugin.utils.getConfigRootDir
import com.icthh.xm.xmeplugin.utils.log
import com.intellij.openapi.project.Project
import getServiceName
import getTenantName
import org.jetbrains.yaml.psi.YAMLValue
import java.io.File
import createProjectFile as createFile

data class YamlContextHelper(
    var psiElement: YAMLValue,
    var fullSpec: Any?,
    var yamlNode: YamlNode,
    var project: Project
) {

    fun createTenantFile(relativePathToConfigRepository: String, body: String?) {
        val path = relativePathToConfigRepository.trimStart('/')
        createFile("/config/tenants/${getTenantName()}/$path", body ?: "")
    }

    fun getTenantName(): String {
        return psiElement.containingFile.getTenantName()
    }

    fun getServiceName(): String? {
        return psiElement.containingFile.getServiceName()
    }

    fun translateToLepConvention(key: String?): String {
        key ?: return ""
        return key.replace("-".toRegex(), "_").replace(".", "$")
    }

    fun createFile(relativePathToConfigRepository: String, body: String) {
        if (!relativePathToConfigRepository.startsWith("/config")) {
            log.error("Path have to be relative to config repository and start with /config")
            throw IllegalArgumentException("Path have to be relative to config repository and start with /config")
        }
        val directory = project.getConfigRootDir() + relativePathToConfigRepository.substringAfter("/config")
        val file = File(directory)
        createFile(file.getParent(), file.getName(), project, body)
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "yamlNode" to yamlNode,
            "fullSpec" to fullSpec,
            "psiElement" to psiElement,
            "project" to project
        )
    }

}
