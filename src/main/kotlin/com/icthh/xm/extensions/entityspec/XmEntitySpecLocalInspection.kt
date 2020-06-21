package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.toPsiFile
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.ERROR
import com.intellij.codeInspection.ProblemHighlightType.WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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
import org.apache.commons.text.similarity.LevenshteinDistance
import org.jetbrains.yaml.YAMLElementGenerator
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

                if (isSectionAttribute(element, "links", "typeKey")) {
                    checkEntityKeyIsExists(element, holder)
                    return
                }

                if (isNextStateKey(element)) {
                    checkNextStateIsExists(element, holder)
                    return
                }

                if (isSectionAttribute(element, "states", "key")) {
                    checkStateKeys(element, holder)
                    return
                }

                if (isSectionAttribute(element, "attachments", "key")) {
                    checkDuplicateKeys(element, holder, "attachments", "key", "link keys")
                    return
                }

                if (isSectionAttribute(element, "calendars", "key")) {
                    checkDuplicateKeys(element, holder, "calendars", "key", "calendar keys")
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

    private fun checkNextStateIsExists(element: LeafPsiElement, holder: ProblemsHolder) {
        val parentSection = element.goSuperParent().goSuperParent() ?: return
        val allStateKeys = getAllStates(parentSection)
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return
        val typeKey = parent.valueText

        val count = allStateKeys.groupingBy { it }.eachCount().get(typeKey) ?: 0
        if (count < 1) {
            val minKeys = minKeys(allStateKeys, typeKey)
            val fixes = minKeys.map { typeKeyQuickFix(it, element) }
            holder.registerProblem(element, "Key $typeKey not found", ERROR, *fixes.toTypedArray())
        }
    }

    private fun minKeys(allStateKeys: List<String>, typeKey: String): Set<String> {
        val levenshtein = LevenshteinDistance()
        val keysDistance = allStateKeys.map { it to levenshtein.apply(typeKey, it) }.toMap()
        val fixKeys = keysDistance.filter { it.value <= 3 }.keys
        val key = keysDistance.minBy { it.value }?.key
        val keys = LinkedHashSet<String>()
        if (key != null) {
            keys.add(key)
        }
        keys.addAll(fixKeys)
        return keys
    }

    private fun typeKeyQuickFix(
        key: String?,
        element: LeafPsiElement
    ): LocalQuickFix {
        return object : LocalQuickFix {
            override fun getFamilyName() = "${key}"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                replaceText(element, key ?: "")
            }
        }
    }

    private fun checkEntityKeyIsExists(
        element: LeafPsiElement,
        holder: ProblemsHolder
    ) {
        val typeKey = element.text.trim()
        val entityKeys = getAllEntityKeys(element)
        val count = entityKeys.groupingBy { it }.eachCount().get(typeKey) ?: 0
        if (count < 1) {
            val minKeys = minKeys(entityKeys, typeKey)
            val fixes = minKeys.map { typeKeyQuickFix(it, element) }
            holder.registerProblem(element, "Entity key $typeKey not found", ERROR, *fixes.toTypedArray())
        }
    }

    private fun replaceText(element: LeafPsiElement, text: String) {
        val elementGenerator = YAMLElementGenerator.getInstance(element.project)
        val colorKeyValue = elementGenerator.createYamlKeyValue("key", text)
        val value = colorKeyValue.value ?: return
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return
        parent.setValue(value)
    }

    private fun checkStateKeys(element: PsiElement, holder: ProblemsHolder) {
        val allStateKeys = getAllStates(element)
        val parent = element.goSuperParent() as? YAMLKeyValue ?: return
        val count = allStateKeys.groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate states key ${parent.valueText}", ERROR)
        }
    }

    private fun getAllStates(element: PsiElement): List<String> {
        val parentSection = element.goParentSection() ?: return listOf()
        val allStateKeys = parentSection.goSubChild().goChild()
            .filterIsInstance<YAMLKeyValue>()
            .filter { it.keyText.equals("key") }
            .map { it.valueText }
        return allStateKeys
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
            .filterIsInstance<YAMLKeyValue>()
            .filter { it.keyText == sectionName }
            .goSubChild().goSubChild()
            .filterIsInstance<YAMLKeyValue>()
            .filter { it.keyText == fieldName }
    }

    private fun getAllEventsKeys(element: PsiElement): List<String> {
        return getAllSubElements(element, "calendars", "events")
            .goSubChild().goSubChild()
            .filterIsInstance<YAMLKeyValue>()
            .map { it.valueText }
    }

    private fun functionKeyCheck(element: PsiElement, holder: ProblemsHolder) {
        val (pathToFunction, functionName) = toFunctionKey(element)
        val functionDirectory = element.getFunctionDirectory()
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

    private fun isEntityKey(position: PsiElement): Boolean {
        val types = position.goParentSection()?.parent
        return position.isChildConfigAttribute("key") && types is YAMLKeyValueImpl && types.keyText.equals("types")
    }

}

fun getFunctionFile(element: PsiElement): VirtualFile? {
    val (pathToFunction, functionName) = toFunctionKey(element)
    val functionDirectory = element.getFunctionDirectory()
    val path = Path.of(functionDirectory + functionName)
    val functionFile = VfsUtil.findFile(path, false)
    return functionFile
}

private fun PsiElement.getFunctionDirectory(): String {
    val entityFolder = this.containingFile.virtualFile.path.substringBeforeLast('/')
    return "${entityFolder}/lep/function/"
}

private fun withEntityId(element: PsiElement) =
    element.goSuperParent()?.parent?.goChild()?.filter {
        it is YAMLKeyValue
    }?.filter {
        it.firstChild.text == "withEntityId"
    }?.filter {
        it.lastChild.text?.toBoolean() ?: false
    }?.isNotEmpty() ?: false

fun toFunctionKey(element: PsiElement): Pair<String, String> {
    val TWO_DOLLARS = "${"$"}${"$"}"
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

fun getAllEntityKeys(
    element: PsiElement
): List<String> {
    return getAllEntityPsiElements(element).map { it.valueText.trim() }
}

fun getAllEntityPsiElements(element: PsiElement): List<YAMLKeyValue> {
    return element.containingFile.goSubChild().goSubChild().goSubChild().goChild()
        .filterIsInstance<YAMLKeyValue>()
        .filter { it.keyText == "key" }
}

fun isSectionAttribute(
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

fun translateToLepConvention(xmEntitySpecKey: String): String {
    Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null")
    return xmEntitySpecKey.replace("-".toRegex(), "_").replace("\\.".toRegex(), "\\$")
}