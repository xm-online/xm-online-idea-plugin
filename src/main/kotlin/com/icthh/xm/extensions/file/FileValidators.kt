package com.icthh.xm.extensions.file

import com.icthh.xm.service.getTenantName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType

class Metrics : AbstractValidator(
    { "/config/tenants/$it/entity/metrics.yml" },
    "metrics.json",
    "Metrics validation"
)

class Tasks : AbstractValidator(
    { "/config/tenants/$it/scheduler/tasks.yml" },
    "tasks.json",
    "Scheduler system task validation"
)

class Uaa : AbstractValidator(
    { "/config/tenants/$it/uaa/uaa.yml" },
    "uaa.json",
    "Uaa validation"
)

abstract class AbstractValidator(val toPath: (String) -> String, val schemaName: String, val message: String) : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(object: JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile) = file.path.endsWith(toPath(file.getTenantName(project)))
            override fun getSchemaFile(): VirtualFile? {
                val classLoader = Metrics::class.java.classLoader
                val url = classLoader.getResource("specs/${schemaName}") ?: return null
                return VfsUtil.findFileByURL(url)
            }

            override fun getSchemaType() = SchemaType.userSchema
            override fun getName(): String = message
        })
    }
}
