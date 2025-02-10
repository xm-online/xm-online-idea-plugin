package com.icthh.xm.xmeplugin.actions

import addToGit
import com.icthh.xm.xmeplugin.extensions.xmentityspec.getEntityInfo
import com.icthh.xm.xmeplugin.extensions.xmentityspec.isEntitySpecification
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VfsUtil
import getTenantName
import isConfigFile
import toVirtualFile
import java.io.File

class GenerateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val entitySpec = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        if (entitySpec == null || !entitySpec.isConfigFile(project)) {
            return
        }

        val psiFile = entitySpec.toPsiFile(project) ?: return
        val entitiesKeys = psiFile.getEntityInfo()?.keys?.toSet()?.toList()?.map {
            "    public static String ${translateToLepConvention(it)} = \"${it}\";\n"
        }?.sorted()?.joinToString("") ?: ""

        val xmEntityDeclarationFile = "class EntityTypeKeys {\n${entitiesKeys}}\n"
        val tenantName = entitySpec.getTenantName(project)
        val path = "${project.basePath}/config/tenants/${tenantName}/entity/lep/commons/generated"
        project.doAsync {
            File(path).mkdirs()
            val file = File("$path/EntityTypeKeys.groovy")
            file.createNewFile()
            file.writeText(xmEntityDeclarationFile)
            project.addToGit(file)

            invokeLater {
                val vfFile = VfsUtil.findFileByIoFile(file, true) ?: return@invokeLater
                project.showInfoNotification(
                    "Entity type key generation",
                    "Entity type keys generated successfully",
                    mapOf("Open file" to { p0, p1 ->
                        OpenFileDescriptor(project, vfFile).navigate(true)
                    })
                )
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        val virtualFile = anActionEvent.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = virtualFile?.isEntitySpecification() ?: false
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
