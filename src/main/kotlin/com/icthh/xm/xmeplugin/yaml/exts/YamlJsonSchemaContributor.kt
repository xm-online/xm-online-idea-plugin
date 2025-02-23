package com.icthh.xm.xmeplugin.yaml.exts

import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.utils.downloadFileContent
import com.icthh.xm.xmeplugin.utils.toPsiFile
import com.icthh.xm.xmeplugin.yaml.toPsiPattern
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import convertPathToUrl
import java.nio.file.Paths

class YamlJsonSchemaContributor: JsonSchemaProviderFactory {
        override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
            val specifications = project.xmePluginSpecService.getSpecifications()
            return specifications.mapNotNull {
                val schemaUrl = project.convertPathToUrl(it.jsonSchemaUrl)
                if (schemaUrl != null) {
                    val schemaPath = downloadFileContent(schemaUrl.toString())
                    val schemaFile = VfsUtil.findFile(Paths.get(schemaPath), false)
                    object: JsonSchemaFileProvider {
                        override fun isAvailable(file: VirtualFile) = it.matchPath(project, file.path)
                        override fun getSchemaFile() = schemaFile
                        override fun getSchemaType() = SchemaType.userSchema
                        override fun getName(): String = "Error file validation by json schema"
                    }
                } else {
                    null
                }
            }
        }
}

class YamlJsonSchemaInFieldContributor: JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        val injections = project.xmePluginSpecService.getSpecifications().flatMap { it.injections }
        return injections.filter { it.jsonSchemaUrl != null }
            .filter { project.convertPathToUrl(it.jsonSchemaUrl) != null }
            .mapNotNull { injection ->
                val schemaUrl = project.convertPathToUrl(injection.jsonSchemaUrl)
                if (schemaUrl == null) {
                    null
                } else {
                    val schemaPath = downloadFileContent(schemaUrl.toString())
                    val schemaFile = VfsUtil.findFile(Paths.get(schemaPath), false)
                    object : JsonSchemaFileProvider {
                        override fun isAvailable(file: VirtualFile): Boolean {
                            if (file is LightVirtualFile) {
                                file.toPsiFile(project)?.let {
                                    val host = InjectedLanguageManager.getInstance(project).getInjectionHost(it)
                                    return injection.elementPath?.toPsiPattern(true)?.accepts(host).isTrue
                                }
                            }
                            return false
                        }

                        override fun getSchemaFile() = schemaFile
                        override fun getSchemaType() = SchemaType.userSchema
                        override fun getName(): String = "Error file validation by json schema"
                    }
                }
            }
    }
}
