package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.services.TENANT_CONFIG_FIELD
import com.icthh.xm.xmeplugin.services.TENANT_CONFIG_FIELD_PATH
import com.icthh.xm.xmeplugin.utils.getConfigRootDir
import com.icthh.xm.xmeplugin.utils.isSupportProject
import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.utils.toPsiFile
import com.icthh.xm.xmeplugin.yaml.findElement
import com.icthh.xm.xmeplugin.yaml.toPsiPattern
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import getTenantName
import java.io.File

class TenantConfigGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<out PsiElement?>? {
        if (sourceElement == null || !sourceElement.project.isSupportProject()) return null
        if (!sourceElement.getUserData(TENANT_CONFIG_FIELD).isTrue) return null

        val project = sourceElement.project
        val path = "${project.getConfigRootDir()}/tenants/${sourceElement.containingFile.getTenantName()}/tenant-config.yml"
        val tenantConfig = VfsUtil.findFile(File(path).toPath(), true)
        val tenantConfigFile = tenantConfig?.toPsiFile(project) ?: return null

        val yamlPath = sourceElement.getUserData(TENANT_CONFIG_FIELD_PATH)
        if (yamlPath != null) {
            return yamlPath.map { it.trimStart('.').toPsiPattern(false) }
                .flatMap { findElement(tenantConfigFile, it) }
                .ifEmpty { listOf(tenantConfigFile) }
                .toTypedArray()
        }

        return arrayOf(tenantConfigFile)
    }

    override fun getActionText(context: DataContext): String {
        return "Open tenant config"
    }
}
