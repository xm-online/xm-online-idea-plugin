package com.icthh.xm.xmeplugin.yaml.exts

import com.icthh.xm.xmeplugin.utils.PsiReferenceImpl
import com.icthh.xm.xmeplugin.utils.log
import com.icthh.xm.xmeplugin.yaml.*
import com.intellij.codeInspection.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import getSpecifications
import mapSeverity
import macthPattern
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import runAction
import runCondition
import runMessageTemplate

class YamlSpecLocalInspection: LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (getSpecifications(holder.file, holder.project).isEmpty()) {
            return EMPTY_VISITOR
        }

        return object : YamlPsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is YAMLScalar) return
                val project = element.project
                getSpecifications(element.containingFile, project).forEach { spec ->
                    spec.inspections
                        .filter { macthPattern(element, it.elementPath) }
                        .forEach { inspection ->
                            runInspection(element, spec, inspection, holder) {
                                runCondition(it, inspection.condition, inspection.includeFunctions, element.project)
                            }
                        }
                }
                getSpecifications(element.containingFile, project).forEach { spec ->
                    spec.references
                        .filter { macthPattern(element, it.elementPath) }
                        .forEach { inspection ->
                            runInspection(element, spec, inspection, holder) {
                                element.references.any {
                                    it is PsiReferenceImpl && it.resolve() == null
                                }
                            }
                        }
                }

            }
        }
    }

    private fun runInspection(
        element: YAMLScalar,
        spec: Specification,
        inspection: LocalInspection,
        holder: ProblemsHolder,
        check: (YamlContext) -> Boolean
    ) {
        try {
            val project = element.project
            val context: YamlContext? = project.xmePluginSpecMetaInfoService.getYamlContext(element, spec.key)
            context?.let {
                executeInspection(context, inspection, element, holder, check)
            }
        } catch (e: Exception) {
            if (e is ControlFlowException) {
                throw e
            }
            element.log.error("Error in runInspection: ${e.message}", e)
        }
    }

    private fun executeInspection(
        context: YamlContext,
        inspection: LocalInspection,
        element: YAMLScalar,
        holder: ProblemsHolder,
        check: (YamlContext) -> Boolean
    ) {
        if (check(context)) {
            val message = runMessageTemplate(
                context,
                inspection.errorMessageTemplate,
                inspection.includeFunctions,
                element.project
            ) ?: "Error validation ${inspection.key}"
            val type: ProblemHighlightType = mapSeverity(inspection)
            if (inspection.action.isNullOrBlank()) {
                holder.registerProblem(element, message, type)
                return
            }

            val actionMessage = runMessageTemplate(
                context,
                inspection.actionMessageTemplate,
                inspection.includeFunctions,
                element.project
            ) ?: "Apply fix to ${inspection.key}"
            holder.registerProblem(element, message, type, object : LocalQuickFix {
                override fun getFamilyName() = actionMessage

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    runAction(context, inspection.action, inspection.includeFunctions, project)
                }
            })

        }
    }

}

