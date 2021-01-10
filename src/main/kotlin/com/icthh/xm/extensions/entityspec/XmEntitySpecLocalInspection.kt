package com.icthh.xm.extensions.entityspec

import com.icthh.xm.service.getRepository
import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.*
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.vcsUtil.VcsUtil
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import git4idea.util.GitFileUtils
import org.apache.commons.text.similarity.LevenshteinDistance
import org.jetbrains.plugins.groovy.lang.psi.util.childrenOfType
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.*
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl
import org.jetbrains.yaml.schema.YamlJsonPsiWalker
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.reflect.KProperty1

const val TWO_DOLLARS = "${"$"}${"$"}"

data class SpecSection(val name: String, val keyExtractor: KProperty1<XmEntitySpecInfo, List<String>>)

class XmEntitySpecLocalInspection: AbstractXmEntitySpecLocalInspection() {

    init {
        addLocalInspection(entitySpecField("key")) { element, holder ->
            lepCreationTip(
                element,
                holder,
                { "Save${TWO_DOLLARS}${it}${TWO_DOLLARS}around.groovy" },
                { "Create entity save lep $it" },
                "/lep/service/entity/"
            )
            moveToSeparateFileTip(element, holder)
            checkEntityKeyDuplication(element, holder)
        }
        addLocalInspection(entitySectionPlace("functions", "key")) { element, holder ->
            functionKeyCheck(element, holder)
            functionCheckDuplicateKeys(element, holder)
        }
        addLocalInspection(entitySectionPlace("links", "key")) { element, holder ->
            checkDuplicateKeys(element, holder, SpecSection("links", XmEntitySpecInfo::linksKeys), "link keys")
        }
        addLocalInspection(entitySectionPlace("links", "typeKey")) { element, holder ->
            checkEntityKeyIsExists(element, holder)
        }
        addLocalInspection(nextStatePlace()) { element, holder ->
            changeStateArcLep(element, holder)
            checkNextStateIsExists(element, holder)
        }
        addLocalInspection(entitySectionPlace("states", "key")) { element, holder ->
            changeStateLep(element, holder)
            checkStateKeys(element, holder)
        }
        listOf(
            SpecSection("attachments", XmEntitySpecInfo::attachmentsKeys),
            SpecSection("calendars", XmEntitySpecInfo::calendarsKeys),
            SpecSection("locations", XmEntitySpecInfo::locationsKeys),
            SpecSection("tags", XmEntitySpecInfo::tagsKeys),
            SpecSection("comments", XmEntitySpecInfo::commentsKeys),
            SpecSection("ratings", XmEntitySpecInfo::ratingsKeys),
        ) .forEach {
            addLocalInspection(entitySectionPlace(it.name, "key")) { element, holder ->
                checkDuplicateKeys(element, holder, it, "${it.name} keys")
            }
        }
        addLocalInspection(calendarEventFieldPlace("key")) { element, holder ->
            checkCalendarEventsKeyDuplication(element, holder)
        }
        addLocalInspection(calendarEventFieldPlace("dataTypeKey")) { element, holder ->
            checkEntityKeyIsExists(element, holder)
        }
        addLocalInspection(allowedStateKeyPlace()) { element, holder ->
            checkStateKeyIsExists(element, holder)
        }
    }

    private fun checkStateKeyIsExists(
        element: LeafPsiElement,
        holder: ProblemsHolder
    ) {
        val entityDefinition = element.getParentOfType<YAMLKeyValue>().getParentOfType<YAMLSequence>()
            .getParentOfType<YAMLSequenceItem>()
        val allStateKeys = entityDefinition?.getStateKeys() ?: return
        val typeKey = element.text

        val count = allStateKeys.groupingBy { it }.eachCount().get(typeKey) ?: 0
        if (count < 1) {
            val minKeys = minKeys(allStateKeys, typeKey)
            val fixes = minKeys.map { typeKeyItemQuickFix(it, element) }
            holder.registerProblem(element, "Key $typeKey not found", ERROR, *fixes.toTypedArray())
        }
    }

    private fun changeStateArcLep(
        element: LeafPsiElement,
        holder: ProblemsHolder
    ) {
        val block = element.findFirstParent { it is YAMLSequence }.findFirstParent { it is YAMLMapping }
        val source = block.findChildOfType<YAMLKeyValue>()?.valueText ?: return
        val entityKey = block.findFirstParent { it is YAMLSequence }
            .findFirstParent { it is YAMLMapping }
            .findChildOfType<YAMLKeyValue>()?.valueText ?: return

        val translatedEntityKey = translateToLepConvention(entityKey)
        val translatedSource = translateToLepConvention(source)
        lepCreationTip(
            element,
            holder,
            { "ChangeState${TWO_DOLLARS}${translatedEntityKey}${TWO_DOLLARS}${translatedSource}${TWO_DOLLARS}${it}${TWO_DOLLARS}around.groovy" },
            { "Create change state lep file $it" },
            "/lep/lifecycle/chained/"
        )
    }

    private fun changeStateLep(
        element: LeafPsiElement,
        holder: ProblemsHolder
    ) {

        val entityKey = element
            .findFirstParent { it is YAMLSequence }
            .findFirstParent { it is YAMLMapping }
            .findChildOfType<YAMLKeyValue>()?.valueText ?: return

        val translatedEntityKey = translateToLepConvention(entityKey)
        lepCreationTip(
            element,
            holder,
            { "ChangeState${TWO_DOLLARS}$translatedEntityKey${TWO_DOLLARS}${it}${TWO_DOLLARS}around.groovy" },
            { "Create change state lep file $it" },
            "/lep/lifecycle/chained/"
        )
    }

    private fun moveToSeparateFileTip(element: LeafPsiElement, holder: ProblemsHolder) {
        val project = holder.project
        val file = holder.file
        val containingDirectory = file.containingDirectory
        val isEntityDir = containingDirectory?.name?.equals("entity") ?: false
        val isEntitySpecFile = "xmentityspec.yml".equals(file.name)
        if (isEntityDir && isEntitySpecFile) {
            val text = element.text
            if (text.contains('.') && text.split(".").first().isNotBlank()) {
                splitXmEntityTip(element, holder, containingDirectory, text.split(".").first().toLowerCase())
            }
            splitXmEntityTip(element, holder, containingDirectory, text.toLowerCase())
        }
    }

    private fun splitXmEntityTip(
        element: LeafPsiElement,
        holder: ProblemsHolder,
        containingDirectory: PsiDirectory,
        fileName: String
    ) {
        val description = "Move entity declaration to separate file"
        holder.registerProblem(element, description, INFORMATION, object : LocalQuickFix {
            override fun getFamilyName() = "Move entity declaration to separate file (xmentityspec/$fileName.yml)"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                var prefix = "---\ntypes:\n"
                val path = containingDirectory.virtualFile.path + "/xmentityspec/${fileName}.yml"
                val file = VfsUtil.findFile(File(path).toPath(), true)
                if (file != null && file.exists()) {
                    prefix = VfsUtil.loadText(file).trimEnd() + "\n"
                }
                ApplicationManager.getApplication().runWriteAction {
                    val seqOffset = element.getParentOfType<YAMLSequence>()?.parent?.childrenOfType<PsiElement>()
                        ?.filter { it is LeafPsiElement }?.last()
                    createFile(
                        containingDirectory.virtualFile.path,
                        "/xmentityspec",
                        "/xmentityspec/${fileName}.yml",
                        project,
                        prefix + "${seqOffset?.text}${element.getParentOfType<YAMLSequenceItem>()?.text}\n"
                    )
                    element.getParentOfType<YAMLSequenceItem>()?.delete()
                    project.xmEntitySpecService.invalidate(element.originalFile.getTenantName(project))
                    project.save()
                }
            }
        })
    }

    private fun lepCreationTip(
        element: LeafPsiElement,
        holder: ProblemsHolder,
        template: (String) -> String,
        message: (String) -> String,
        lepGroup: String
    ): Boolean {
        val (lepKey, _) = toLepKey(element.text)
        val lepName = template.invoke(lepKey)
        val lepDirectory = element.getRootFolder() + lepGroup
        val path = Path.of(lepDirectory + lepName)
        val lepFile = VfsUtil.findFile(path, false)

        if (lepFile != null) {
            return true
        }
        val description = "You can override logic by creating LEP"
        holder.registerProblem(element, description, INFORMATION, object : LocalQuickFix {
            override fun getFamilyName() = message.invoke(lepName)

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                ApplicationManager.getApplication().runWriteAction {
                    createLepFile(lepDirectory, lepName, project)
                }
            }
        })
        return false
    }

    private fun checkNextStateIsExists(element: LeafPsiElement, holder: ProblemsHolder) {
        val stateSequence = element.getParentOfType<YAMLSequence>() ?: return
        val allStateKeys = getAllStates(stateSequence)
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return
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
        val key = keysDistance.minByOrNull { it.value }?.key
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
            override fun getFamilyName() = "$key"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                replaceText(element, key ?: "")
            }
        }
    }

    private fun typeKeyItemQuickFix(
        key: String?,
        element: LeafPsiElement
    ): LocalQuickFix {
        return object : LocalQuickFix {
            override fun getFamilyName() = "$key"

            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                val parent = element.getParentOfType<YAMLScalarImpl>() ?: return
                parent.updateText(key ?: "")
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
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return
        parent.setValue(value)
    }

    private fun checkStateKeys(element: PsiElement, holder: ProblemsHolder) {
        val allStateKeys = getAllStates(element)
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return
        val count = allStateKeys.groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate states key ${parent.valueText}", ERROR)
        }
    }

    private fun getAllStates(element: PsiElement): List<String> {
        return element.withCache {
            element.getParentOfType<YAMLSequence>().mapToFields("key").map { it.valueText }
        }
    }

    private val ProblemsHolder.spec get() = project.xmEntitySpecService.getEntitySpec(file.originalFile)

    private fun checkCalendarEventsKeyDuplication(element: PsiElement, holder: ProblemsHolder) {
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return
        val count = holder.spec.eventsKeys.groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate events key ${parent.valueText}", ERROR)
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
        section: SpecSection,
        message: String
    ) {
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return
        val count = section.keyExtractor.get(holder.spec).groupingBy { it }.eachCount().get(parent.valueText) ?: 0
        if (count > 1) {
            holder.registerProblem(element, "Duplicate ${message} ${parent.valueText}", ERROR)
        }
    }

    private fun functionCheckDuplicateKeys(
        element: PsiElement,
        holder: ProblemsHolder
    ) {
        val parent = element.getParentOfType<YAMLKeyValue>() ?: return

        val functionName = translateToLepConvention(parent.valueText)
        val count = holder.spec.functionKeys
            .map { translateToLepConvention(it) }
            .groupingBy { it }.eachCount().get(functionName) ?: 0
        if (count > 1) {
            val (_, fullFunctionName) = toFunctionKey(element)
            holder.registerProblem(element, "Duplicate ${fullFunctionName} name", ERROR)
        }
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
                    createLepFile(functionDirectory, functionName, project, pathToFunction)
                }
            }
        })
    }

    private fun createLepFile(
        lepDirectory: String,
        lepName: String,
        project: Project,
        pathToLep: String = ""
    ) {
        createFile(lepDirectory, pathToLep, lepName, project, "return null")
    }

    private fun createFile(
        directory: String,
        path: String = "",
        name: String,
        project: Project,
        body: String
    ) {
        File(directory + path).mkdirs()
        val file = File(directory + name)
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(body)
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return
        val psiFile = virtualFile.toPsiFile(project)
        psiFile?.navigate(true)

        val repository = project.getRepository()
        GitFileUtils.addPaths(project, repository.root, listOf(VcsUtil.getFilePath(virtualFile)))
    }

}

class XmEntitySpecSchemaInspection : YamlJsonSchemaHighlightingInspection() {

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
            override fun visitElement(element: PsiElement) {
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
            }
        }

    }
}

abstract class AbstractXmEntitySpecLocalInspection(
    val localInspections: MutableList<LocalInspection> = ArrayList()
): LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isEntitySpecification()) {
            return EMPTY_VISITOR
        }

        return object : YamlPsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                start("localInspection")
                doWork(element)
                stop("localInspection")
            }

            private fun doWork(element: PsiElement?) {
                if (element == null) return
                if (element !is LeafPsiElement) return
                localInspections.forEachIndexed { index, localInspection ->
                    if (localInspection.process(element, holder)) {
                        return
                    }
                }
            }
        }
    }

    fun addLocalInspection(pattern: ElementPattern<out PsiElement>,
                           inspection: (element: LeafPsiElement, holder: ProblemsHolder) -> Unit) {
        localInspections.add(LocalInspection(pattern, inspection))
    }

}

data class LocalInspection(val pattern: ElementPattern<out PsiElement>,
                           val inspection: (element: LeafPsiElement, holder: ProblemsHolder) -> Unit) {
    fun process(element: LeafPsiElement, holder: ProblemsHolder): Boolean {
        if (pattern.accepts(element)) {
            inspection.invoke(element, holder)
            return true
        }
        return false
    }
}

private fun PsiElement?.findFirstParent(condition: (PsiElement?) -> Boolean) = this?.findFirstParent(true, condition)

fun getFunctionFile(element: PsiElement): VirtualFile? {
    val (_, functionName) = toFunctionKey(element)
    val functionDirectory = element.getFunctionDirectory()
    val path = Path.of(functionDirectory + functionName)
    val functionFile = VfsUtil.findFile(path, false)
    return functionFile
}

private fun PsiElement.getFunctionDirectory(): String {
    return "${getRootFolder()}/lep/function/"
}

private fun PsiElement.getRootFolder(): String {
    val project = this.project
    val tenantName = this.originalFile.getTenantName(project)
    val path = "${project.basePath}/config/tenants/${tenantName}/entity"
    return path
}
