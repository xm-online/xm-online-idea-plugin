package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.logger
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.ERROR
import com.intellij.codeInspection.ProblemHighlightType.WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import org.jetbrains.yaml.schema.YamlJsonPsiWalker
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class XmEntitySpecLocalInspection: YamlJsonSchemaHighlightingInspection() {

    val TWO_DOLLARS = "${"$"}${"$"}"
    val schemas: MutableMap<String, JsonSchemaObject?> = ConcurrentHashMap()

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

        val schema = schemas.computeIfAbsent(holder.project.locationHash) {
            JsonCachedValues.getSchemaObject(getSchemaFile(), holder.project)
        }
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

                if (element !is LeafPsiElement) return

                // TODO check key duplication on entity key, state key inside entity, events

                if (isEntityKey(element)) {
                    checkEntityKeyDuplication(element, holder)
                    return
                }

                if (isSectionAttribute(element, "functions", "key")) {
                    functionKeyCheck(element, holder)
                    functionCheckDuplicateKeys(element, holder)
                    return
                }

                if (isSectionAttribute(element, "links", "key")) {
                    checkDuplicateKeys(element, holder, "links", "key", "link keys")
                    return
                }

                if (isSectionAttribute(element, "attachments", "key")) {
                    checkDuplicateKeys(element, holder, "attachments", "key", "link keys")
                    return
                }

                if (isSectionAttribute(element, "calendars", "key")) {
                    checkDuplicateKeys(element, holder, "calendars", "key", "calendar keys")
                    return
                }

                if (isCalendarEventsKey(element)) {
                    checkCalendarEventsKeyDuplication(element, holder)
                    return
                }

                if (isSectionAttribute(element, "locations", "key")) {
                    checkDuplicateKeys(element, holder, "locations", "key", "location keys")
                    return
                }

                if (isSectionAttribute(element, "tags", "key")) {
                    checkDuplicateKeys(element, holder, "tags", "key", "tag keys")
                    return
                }

                if (isSectionAttribute(element, "comments", "key")) {
                    checkDuplicateKeys(element, holder, "comments", "key", "comment keys")
                    return
                }

                if (isSectionAttribute(element, "ratings", "key")) {
                    checkDuplicateKeys(element, holder, "comments", "key", "comment keys")
                    return
                }

            }
        }
    }

    private fun checkCalendarEventsKeyDuplication(element: PsiElement, holder: ProblemsHolder) {
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return
        val count = getAllEventsKeys(element).groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate events key ${parent.valueText}", ERROR)
        }
    }

    private fun isCalendarEventsKey(element: PsiElement): Boolean {
        if(element.isChildConfigAttribute("key")) {
            val events = element.goSuperParent() ?: return false
            val event = events.goSuperParent() ?: return false
            return isSectionAttribute(event, "calendars", "events")
        } else {
            return false
        }
    }

    private fun checkEntityKeyDuplication(
        element: LeafPsiElement,
        holder: ProblemsHolder
    ) {
        val entityKey = element.text.trim()
        val entityKeys = getAllEntityKeys(element)
        val count = entityKeys.groupingBy { it }.eachCount().get(entityKey) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate entity key ${entityKey}", ERROR)
        }
    }

    private fun checkDuplicateKeys(
        element: PsiElement,
        holder: ProblemsHolder,
        section: String,
        attribute: String,
        message: String
    ) {
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return

        val count = getAllKeys(parent, section, attribute).groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate ${message} ${parent.valueText}", ERROR)
        }
    }

    private fun functionCheckDuplicateKeys(
        element: PsiElement,
        holder: ProblemsHolder
    ) {
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return

        val functionName = translateToLepConvention(parent.valueText)
        val count = getAllKeys(parent, "functions", "key")
            .map { translateToLepConvention(it) }
            .groupingBy { it }.eachCount().get(functionName) ?: 0
        if (count > 1) {
            val (_, fullFunctionName) = toFunctionKey(element)
            holder.registerProblem(element, "Duplicate ${fullFunctionName} name", ERROR)
        }
    }

    private fun getAllEntityKeys(
        element: PsiElement
    ): List<String> {
        return element.containingFile.goSubChild().goSubChild().goSubChild().goChild()
            .filter { it is YAMLKeyValue}
            .map { it as YAMLKeyValue }
            .filter{ it.keyText == "key" }
            .map { it.valueText.trim() }
    }

    private fun getAllKeys(
        element: PsiElement,
        sectionName: String,
        fieldName: String
    ): List<String> {
        return getAllSubElements(element, sectionName, fieldName).map { it.valueText.trim() }
    }

    private fun getAllSubElements(
        element: PsiElement,
        sectionName: String,
        fieldName: String
    ): List<YAMLKeyValue> {
        return element.containingFile.goSubChild().goSubChild().goSubChild().goChild()
            .filter { it is YAMLKeyValue }
            .map { it as YAMLKeyValue }
            .filter { it.keyText == sectionName }
            .goSubChild().goSubChild()
            .filter { it is YAMLKeyValue }
            .map { it as YAMLKeyValue }
            .filter { it.keyText == fieldName }
    }

    private fun getAllEventsKeys(element: PsiElement): List<String> {
        return getAllSubElements(element, "calendars", "events")
            .goSubChild().goSubChild()
            .filter { it is YAMLKeyValue }
            .map { it as YAMLKeyValue }
            .map { it.valueText }
    }

    private fun functionKeyCheck(element: PsiElement, holder: ProblemsHolder) {
        val entityFolder = element.containingFile.virtualFile.path.substringBeforeLast('/')
        val (pathToFunction, functionName) = toFunctionKey(element)
        val functionDirectory = "${entityFolder}/lep/function/"
        val path = Path.of(functionDirectory + functionName)
        val functionFile = VfsUtil.findFile(path, false)

        if (functionFile != null) {
            return
        }

        holder.registerProblem(element, "Function ${functionName} not found", WARNING, object : LocalQuickFix {
            override fun getFamilyName() = "Create function file ${functionName}"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                ApplicationManager.getApplication().runWriteAction {
                    createFunction(functionDirectory, pathToFunction, functionName, project)
                }
            }
        })
    }

    private fun toFunctionKey(element: PsiElement): Pair<String, String> {
        var functionKey = translateToLepConvention(element.text.trim().trimStart('/'))
        var pathToFunction = functionKey.substringBeforeLast('/', "")
        if (pathToFunction.isNotBlank()) {
            pathToFunction += '/'
        }
        functionKey = functionKey.substringAfterLast('/')
        val functionPrefix = if (withEntityId(element)) {
            "FunctionWithXmEntity"
        } else {
            "Function"
        }
        val functionName = "${pathToFunction}${functionPrefix}${TWO_DOLLARS}${functionKey}${TWO_DOLLARS}tenant.groovy"
        return Pair(pathToFunction, functionName)
    }


    private fun createFunction(
        functionDirectory: String,
        pathToFunction: String,
        functionName: String,
        project: Project
    ) {
        File(functionDirectory + pathToFunction).mkdirs()
        val file = File(functionDirectory + functionName)
        file.createNewFile()
        file.writeText("return [:]")
        val functionVf = VfsUtil.findFileByIoFile(file, true)
        val psiFile = functionVf?.toPsiFile(project)
        psiFile?.navigate(true)
    }

    private fun withEntityId(element: PsiElement) =
        element.goSuperParent()?.parent?.goChild()?.filter {
            it is YAMLKeyValue
        }?.filter {
            it.firstChild.text == "withEntityId"
        }?.filter {
            it.lastChild.text?.toBoolean() ?: false
        }?.isNotEmpty() ?: false

    private fun isSectionAttribute(
        position: PsiElement,
        section: String,
        attrName: String
    ): Boolean {
        //val timer = StopWatch.createStarted();
        val result = (position.isChildConfigAttribute(attrName)
                && isConfigSection(position, section)
                && isUnderEntityConfig(position))
        //logger.info("isFunctionAttribute ${timer.getTime(MILLISECONDS)}ms")
        return result
    }

    private fun isEntityKey(position: PsiElement): Boolean {
        val types = position.goParentSection()?.parent
        return position.isChildConfigAttribute("key") && types is YAMLKeyValueImpl && types.keyText.equals("types")
    }

}

fun translateToLepConvention(xmEntitySpecKey: String): String {
    Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null")
    return xmEntitySpecKey.replace("-".toRegex(), "_").replace("\\.".toRegex(), "\\$")
}