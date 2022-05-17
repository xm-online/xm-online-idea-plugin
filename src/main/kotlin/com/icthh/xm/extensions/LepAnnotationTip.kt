package com.icthh.xm.extensions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.WebDialog
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.service.*
import com.icthh.xm.utils.logger
import com.intellij.codeInspection.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotationParameterList
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.vcsUtil.VcsUtil
import git4idea.util.GitFileUtils
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import java.awt.Dimension
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

private const val LOGIC_EXTENSION_POINT = "com.icthh.xm.commons.lep.LogicExtensionPoint"
private const val LEP_SERVICE = "com.icthh.xm.commons.lep.spring.LepService"

class LepAnnotationTip : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {

        return object : JavaElementVisitor() {
            override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
                val annotation = list.context as PsiAnnotationImpl
                if (annotation.qualifiedName == LOGIC_EXTENSION_POINT) {
                    val params = list.attributes.map { it.name to it.value?.text }.toMap()
                    val lepName = params["value"]?.removeSurrounding("\"", "\"")
                    val hasResolver = params["resolver"] != null
                    if (lepName == null) {
                        return
                    }

                    val psiMethod = annotation.context?.context as? PsiMethodImpl
                    val containingClass = psiMethod?.containingClass ?: return
                    val lepServiceAnnotation =  containingClass.annotations.find { it.qualifiedName == LEP_SERVICE }
                    val group = params["group"] ?: lepServiceAnnotation?.findAttributeValue("group").stringValue()

                    val project = annotation.project
                    val selected = project.getSettings().selected() ?: return
                    val basePath = project.basePath ?: return
                    if (selected.selectedTenants.isEmpty()) {
                        return
                    }

                    val description = "Create lep file"
                    holder.registerProblem(annotation, description,
                        ProblemHighlightType.INFORMATION, object : LocalQuickFix {
                        override fun getFamilyName() = description

                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            createLepFile(
                                project,
                                basePath,
                                group,
                                LepDialogContext(
                                    lepName,
                                    hasResolver,
                                    selected.selectedTenants,
                                    containingClass.isInterface
                                )
                            )
                        }
                    })
                }
            }
        }
    }

    private fun createLepFile(
        project: Project,
        basePath: String,
        group: String?,
        context: LepDialogContext
    ) {

        val fileListDialog = CreateLepDialog(project, context)
        fileListDialog.show()
        if (fileListDialog.isOK) {
            ApplicationManager.getApplication().runWriteAction {
                val data = fileListDialog.data
                logger.info(">> ${data}")
                //createFile(basePath, group, context.lepName, project, "return null")
            }
        }
    }

    private fun createFile(
        directory: String,
        group: String?,
        name: String,
        project: Project,
        body: String
    ) {
        File(directory + group).mkdirs()
        val file = File(directory + name)
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(body)
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return
        val psiFile = virtualFile.toPsiFile(project)
        psiFile?.navigate(true)

        val repository = project.getRepository() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            GitFileUtils.addPaths(project, repository.root, listOf(VcsUtil.getFilePath(virtualFile)))
        }
    }

}


class CreateLepDialog(project: Project, val context: LepDialogContext): WebDialog(
    project = project, viewName = "create-lep-dialog", dialogTitle = "Create lep dialog",
    dimension = Dimension(600, 200)
) {

    val mapper = jacksonObjectMapper()
    var data: LepDialogState = LepDialogState("", "", false)

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") { body, pipe ->
                logger.info("component ready")
                pipe.post("initData", mapper.writeValueAsString(context))
            },
            BrowserCallback("onUpdate") { body, pipe ->
                logger.info("on update")
                data = mapper.readValue(body)
            }
        )
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return null
    }

}

data class LepDialogState (
    val lepKey: String,
    val tenant: String,
    val generateCodeSnipped: Boolean
)

data class LepDialogContext(
    val lepName: String,
    val hasResolver: Boolean,
    val tenants: Set<String>,
    val isInterface: Boolean
)
