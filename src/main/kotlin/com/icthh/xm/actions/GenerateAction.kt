package com.icthh.xm.actions

import com.icthh.xm.extensions.entityspec.getAllEntitiesKeys
import com.icthh.xm.extensions.entityspec.getTenantName
import com.icthh.xm.extensions.entityspec.translateToLepConvention
import com.icthh.xm.service.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.util.GitFileUtils
import java.io.File

class GenerateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return
        val file = e.getDataContext().getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || !file.isConfigFile(project)) {
            return
        }

        val psiFile = file.toPsiFile(project) ?: return
        val entitiesKeys = getAllEntitiesKeys(project, psiFile).map {
            "    public static String ${translateToLepConvention(it)} = \"${it}\";\n";
        }.joinToString("")

        val xmEntityDeclarationFile = "class EntityTypeKeys {\n${entitiesKeys}}\n"

        val tenantName = file.getTenantName(project)
        val path = "${project.basePath}/config/tenants/${tenantName}/entity/lep/commons/generated"

        ApplicationManager.getApplication().runWriteAction {
            File(path).mkdirs()
            val file = File("$path/EntityTypeKeys\$\$tenant.groovy")
            file.createNewFile()
            file.writeText(xmEntityDeclarationFile)
            val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return@runWriteAction
            ApplicationManager.getApplication().executeOnPooledThread {
                val repository = project.getRepository() ?: return@executeOnPooledThread
                GitFileUtils.addPaths(project, repository.root, listOf(VcsUtil.getFilePath(virtualFile)))
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project
        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
    }
}
