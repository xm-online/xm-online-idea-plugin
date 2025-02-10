package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.yaml.YamlUtils
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import getTenantName
import java.io.File

val IS_ENTITY_SPEC: Key<Boolean> = Key.create("IS_ENTITY_SPEC")

fun PsiElement.isEntitySpecification(): Boolean {
    return this.containingFile.virtualFile.isEntitySpecification()
}

fun VirtualFile?.isEntitySpecification(): Boolean {
    this ?: return false
    if (this.getUserData(IS_ENTITY_SPEC) != null) {
        return this.getUserData(IS_ENTITY_SPEC)!!
    }
    val containingDirectory = this.parent
    val isEntitySpecDir = containingDirectory?.name?.equals("xmentityspec") ?: false
    val isYamlFile = name.endsWith(".yml")
    if (isEntitySpecDir && isYamlFile) {
        return true
    }
    val isEntityDir = containingDirectory?.name?.equals("entity") ?: return false
    val isEntitySpecFile = "xmentityspec.yml".equals(name)
    val entitySpec = isEntityDir && isEntitySpecFile
    this.putUserData(IS_ENTITY_SPEC, entitySpec)
    return entitySpec
}

fun PsiFile.getEntityInfo(): XmEntitySpecInfo? {
    val containingFile = this.containingFile
    val tenantName = containingFile.getTenantName()
    val entityFiles = this.project.computeEntityFiles(tenantName)
    val key = "entityInfoByFile-${entityFiles.values.map { it.name }.joinToString("-")}"
    try {
        val entitySpec = this.project.withMultipleFilesCache(key, entityFiles.values) {
            log.info("Computing entity info for tenant $tenantName")
            entityFiles.map {
                val value = it.value
                value.withCache("entityInfo-${tenantName}-${value.name}") {
                    value.computeXmEntitySpecInfo(tenantName)
                }
            }.joinEntitySpecInfo(tenantName)
        }
        this.project.putUserData(LAST_ENTITY_SPEC, entitySpec)
    } catch (e: Exception) {
        log.warn("Error while computing entity info for tenant $tenantName", e)
    }
    return this.project.getUserData(LAST_ENTITY_SPEC)
}
val LAST_ENTITY_SPEC = Key.create<XmEntitySpecInfo>("LAST_ENTITY_SPEC")


data class XmEntitySpecInfo(val keys: MutableList<String> = mutableListOf())

fun buildXmEntitySpecInfo(tenantName: String, rawInfo: Any?): XmEntitySpecInfo {
    val xmEntitySpecInfo = XmEntitySpecInfo()
    xmEntitySpecInfo.keys.addAll(rawInfo.keyItem("types").mapItem { it.keyItem("key") } as Collection<String>)
    return xmEntitySpecInfo
}

private fun List<XmEntitySpecInfo>.joinEntitySpecInfo(tenantName: String): XmEntitySpecInfo {
    val xmEntitySpec = XmEntitySpecInfo()
    this.forEach {
        xmEntitySpec.keys.addAll(it.keys)
    }
    return xmEntitySpec
}

fun PsiFile.computeXmEntitySpecInfo(tenantName: String): XmEntitySpecInfo {
    val rawInfo = YamlUtils.readYaml(this.text)
    return buildXmEntitySpecInfo(tenantName, rawInfo)
}

private fun Project.computeEntityFiles(tenantName: String): Map<String, PsiFile> {
    val files = HashMap<String, PsiFile>()
    val path = "/config/tenants/${tenantName}/entity/xmentityspec"
    val specYml = VfsUtil.findFile(File("${basePath}/$path.yml").toPath(), true)
    val psiFile = specYml?.toPsiFile(this)
    psiFile?.let { files.put("$path.yml", it) }

    val specDirectory = VfsUtil.findFile(File("${basePath}/$path").toPath(), true)
    specDirectory?.children?.toList()?.mapNotNull { it.toPsiFile(this) }?.forEach {
        files.put(path + "/" + it.name, it)
    }
    return files
}

fun PsiElement.getEntityFunctionDirectory(): String {
    return "${getEntityRootFolder()}/lep/function/"
}

fun PsiElement.getEntityRootFolder(): String {
    val tenantName = this.originalFile.getTenantName()
    return "${project.basePath}/config/tenants/${tenantName}/entity"
}

