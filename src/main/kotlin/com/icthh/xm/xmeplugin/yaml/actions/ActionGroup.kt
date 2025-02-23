package com.icthh.xm.xmeplugin.yaml.actions

import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.yaml.Action
import com.icthh.xm.xmeplugin.yaml.YamlContext
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecMetaInfoService
import com.icthh.xm.xmeplugin.yaml.xmePluginSpecService
import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findParentOfType
import macthPattern
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import runAction
import runMessageTemplate


class ActionGroup : ActionGroup() {
    override fun getChildren(p0: AnActionEvent?): Array<AnAction> {
        val event = p0 ?: return emptyArray()
        val project = event.project ?: return emptyArray()
        if (!project.isConfigProject()) {
            return emptyArray()
        }

        val file = event.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return emptyArray()
        val specs = project.xmePluginSpecService.getSpecifications(file)
        val element = file.getElementArCursor(event) ?: return emptyArray()
        val yamlElement = element.findParentOfType<YAMLPsiElement>()
        val yamlScalar = if (yamlElement is YAMLScalar) yamlElement else yamlElement.findChildOfType<YAMLScalar>()
        yamlScalar ?: return emptyArray()

        val actions = mutableListOf<AnAction>()

        specs.forEach { spec ->
            val nodeContext = project.xmePluginSpecMetaInfoService.getYamlContext(yamlScalar, spec.key) ?: return@forEach
            spec.actions
                .filter {
                    it.elementPath == null || macthPattern(yamlScalar, it.elementPath)
                }
                .filter {
                    runCondition(nodeContext, it.condition, it.includeFunctions, project)
                }.forEach {
                    val actionName = runMessageTemplate(nodeContext, it.nameTemplate, it.includeFunctions, project) ?: it.key ?: "Name not specified"
                    actions.add(createAction(actionName, nodeContext, it, project))
                }
        }

        return actions.toTypedArray()
    }

    private fun createAction(
        actionName: String,
        nodeContext: YamlContext,
        it: Action,
        project: Project
    ) = object : AnAction(actionName) {
        override fun actionPerformed(e: AnActionEvent) {
            try {
                runAction(nodeContext, it.action, it.includeFunctions, project)

                if (it.successMessageTemplate != null || it.successActions.isNotEmpty()) {
                    val successMessage = runMessageTemplate(
                        nodeContext,
                        it.successMessageTemplate,
                        it.includeFunctions,
                        project
                    ) ?: "'${actionName}' completed successfully"

                    val messageActions = mutableMapOf<String, (AnActionEvent, Notification) -> Unit>()
                    it.successActions.forEach { successAction ->
                        val successActionName = runMessageTemplate(
                            nodeContext,
                            successAction.actionNameTemplate,
                            it.includeFunctions,
                            project
                        ) ?: return@forEach
                        messageActions[successActionName] = { action, notification ->
                            runAction(nodeContext, successAction.action, it.includeFunctions, project)
                        }
                    }
                    project.showInfoNotification(actionName, successMessage, messageActions)

                }
            } catch (e: Exception) {
                project.showErrorNotification("Error", { e.message ?: "Unknown error" })
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.BGT
        }
    }

    fun runCondition(
        context: YamlContext,
        condition: String?,
        includeFunctions: List<String>?,
        project: Project
    ): Boolean {
        if (condition.isNullOrBlank()) {
            return true
        }
        val functions = project.xmePluginSpecService.getFunctions(includeFunctions ?: emptyList())
        return project.jsRunner.runJsCondition(functions, condition, context)
    }

    private fun PsiFile.getElementArCursor(e: AnActionEvent): PsiElement? {
        val editor = e.getData(CommonDataKeys.EDITOR)
        editor ?: return null
        val offset: Int = editor.caretModel.offset
        return this.findElementAt(offset)
    }

    override fun update(anActionEvent: AnActionEvent) {
        anActionEvent.updateSupported() ?: return
        val project = anActionEvent.project
        anActionEvent.presentation.isEnabled = project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
