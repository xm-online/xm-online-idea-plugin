import com.icthh.xm.xmeplugin.utils.jsRunner
import com.icthh.xm.xmeplugin.utils.log
import com.icthh.xm.xmeplugin.yaml.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

fun mapSeverity(inspection: LocalInspection): ProblemHighlightType {
    val type: ProblemHighlightType = when (inspection.severity) {
        Severity.ERROR -> ERROR
        Severity.WARN -> WARNING
        Severity.INFO -> INFORMATION
        else -> ERROR
    }
    return type
}

fun runAction(
    context: YamlContext,
    action: String?,
    includeFunctions: List<String>?,
    project: Project
) {
    if (action.isNullOrBlank()) {
        return
    }
    val functions = project.xmePluginSpecService.getFunctions(includeFunctions ?: emptyList())
    project.jsRunner.runJsScript(functions, action, context)
}

fun runMessageTemplate(
    context: YamlContext,
    errorMessageTemplate: String?,
    includeFunctions: List<String>?,
    project: Project
): String? {
    if (errorMessageTemplate.isNullOrBlank()) {
        return null
    }
    val functions = project.xmePluginSpecService.getFunctions(includeFunctions ?: emptyList())
    return project.jsRunner.runJsTemplate(functions, errorMessageTemplate, context)
}

fun runCondition(
    context: YamlContext,
    condition: String?,
    includeFunctions: List<String>?,
    project: Project
): Boolean {
    if (condition.isNullOrBlank()) {
        return false
    }
    val functions = project.xmePluginSpecService.getFunctions(includeFunctions ?: emptyList())
    return project.jsRunner.runJsCondition(functions, condition, context)
}

fun macthPattern(
    element: PsiElement,
    elementPath: String?
): Boolean {
    try {
        if (elementPath.isNullOrBlank()) {
            return false
        }
        val pattern = element.project.xmePluginSpecService.parsePattern(elementPath)
        return pattern.accepts(element)
    } catch (e: Exception) {
        if (e is ControlFlowException) {
            throw e
        }
        element.log.error("Error run elementPattern ${elementPath}", e)
        return false
    }
}

fun getSpecifications(psiFile: PsiFile?, project: Project): List<Specification> {
    return project.xmePluginSpecService.getSpecifications(psiFile)
}
