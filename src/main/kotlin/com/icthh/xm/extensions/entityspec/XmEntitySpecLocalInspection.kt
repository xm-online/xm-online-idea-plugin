package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.logger
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import org.jetbrains.yaml.schema.YamlJsonPsiWalker
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.nio.file.Path
import java.util.*
import java.io.IOException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import org.apache.commons.io.IOUtils
import java.io.File


class XmEntitySpecLocalInspection: YamlJsonSchemaHighlightingInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isEntitySpecification()) {
            return EMPTY_VISITOR
        }

        val file = holder.file as? YAMLFile ?: return EMPTY_VISITOR
        val roots = YamlJsonPsiWalker.INSTANCE.getRoots(file)
        if (roots.isEmpty()) {
            return EMPTY_VISITOR
        }
        val service = JsonSchemaService.Impl.get(file.project)
        val virtualFile = file.viewProvider.virtualFile
        if (!service.isApplicableToFile(virtualFile)) {
            return EMPTY_VISITOR
        }

        val schema = JsonCachedValues.getSchemaObject(getSchemaFile(), holder.project)
        schema ?: return EMPTY_VISITOR

        val options = JsonComplianceCheckerOptions(myCaseInsensitiveEnum)
        return object : YamlPsiElementVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element == null) return
                if (roots.contains(element)) {
                    val walker = JsonLikePsiWalker.getWalker(element, schema) ?: return
                    JsonSchemaComplianceChecker(
                        schema,
                        holder,
                        walker,
                        session,
                        options,
                        "XmEntity specification validation: "
                    ).annotate(element)
                }

                // TODO check key duplication on every config (entity, links, function etc...)

                functionKeyCheck(element, holder)
            }
        }
    }

    private fun functionKeyCheck(element: PsiElement, holder: ProblemsHolder) {
        if (isFunctionAttribute(element, "key")) {
            val entityFolder = element.containingFile.virtualFile.path.substringBeforeLast('/')
            var functionKey = translateToLepConvention(element.text.trim().trimStart('/'))
            var pathToFunction = functionKey.substringBeforeLast('/', "")
            if (pathToFunction.isNotBlank()) {
                pathToFunction += '/'
            }
            functionKey = functionKey.substringAfterLast('/')
            val twoDollars = "${"$"}${"$"}"
            val functionPrefix = if (withEntityId(element)) { "FunctionWithXmEntity" } else { "Function" }
            val functionName = "${pathToFunction}${functionPrefix}${twoDollars}${functionKey}${twoDollars}tenant.groovy"
            val functionDirectory = "${entityFolder}/lep/function/"
            val path = Path.of(functionDirectory + functionName)
            val functionFile = VfsUtil.findFile(path, false)

            if (functionFile != null) {
                return
            }

            holder.registerProblem(element, "Function ${functionName} not found", WARNING, object : LocalQuickFix {
                override fun getFamilyName() = "Create function file ${functionName}"

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    ApplicationManager.getApplication().runWriteAction(object : Runnable {
                        override fun run() {
                                File(functionDirectory + pathToFunction).mkdirs()
                                val file = File(functionDirectory + functionName)
                                file.createNewFile()
                                file.writeText("return [:]")
                                val functionVf = VfsUtil.findFileByIoFile(file, true)
                                val psiFile = functionVf?.toPsiFile(project)
                                psiFile?.navigate(true)
                        }
                    })
                }
            })
        }
    }

    private fun withEntityId(element: PsiElement) =
        element.goSuperParent().goSuperParent().goSuperParent()?.goChild()?.filter {
            it is YAMLKeyValue
        }?.filter {
            it.firstChild.text == "withEntityId"
        }?.filter {
            it.lastChild.text?.toBoolean() ?: false
        }?.isNotEmpty() ?: false

    private fun isFunctionAttribute(position: PsiElement, attrName: String): Boolean {
        return (position.isChildConfigAttribute(attrName)
                && isConfigSection(position, "functions")
                && isUnderEntityConfig(position))
    }

}

fun translateToLepConvention(xmEntitySpecKey: String): String {
    Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null")
    return xmEntitySpecKey.replace("-".toRegex(), "_").replace("\\.".toRegex(), "\\$")
}