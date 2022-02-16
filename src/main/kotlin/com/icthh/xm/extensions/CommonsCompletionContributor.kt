package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.getLinkedConfigRootDir
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import java.io.File
import java.util.*

const val COMMONS_PREFIX = "Commons$$"
const val COMMONS_SUFFIX_AROUND = "$${"$"}around.groovy"
const val COMMONS_SUFFIX_TENANT = "$${"$"}tenant.groovy"

class CommonsCompletionContributor: CompletionContributor() {
    init {

        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
                public override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    resultSet: CompletionResultSet
                ) {
                    val parent = parameters.position.parent
                    val commons = "lepContext.commons."

                    val project = parameters.position.project
                    val lepCommons = getLepFolder(project, parameters.position)?.children?.find { it.name == "commons" }
                    if (lepCommons != null && parent != null && parent.text.startsWith(commons)) {

                        var commonsPath = parent.text.substringAfter(commons)
                        if (commonsPath.endsWith(DUMMY_IDENTIFIER_TRIMMED.decapitalize())) {
                            commonsPath = commonsPath.substringBefore(DUMMY_IDENTIFIER_TRIMMED.decapitalize())
                        }

                        val commonsParts = commonsPath.split(".")
                        val variants = getVariants(commonsParts, lepCommons)
                        resultSet.addAllElements(variants.map { LookupElementBuilder.create(it) })
                    }
                }
            }
        )

    }

}

public fun getVariants(commonsParts: List<String>, lepCommons: VirtualFile): List<String> {
    var folder = lepCommons
    val parts = LinkedList(commonsParts)
    val last = parts.removeLast()

    for (part in parts) {
        folder = folder.children.find { it.name == part } ?: return listOf()
    }
    val variants = ArrayList<String>()
    variants.addAll(folder.children.filter { it.isDirectory }.map { it.name })
    val variantMethods = folder.children.filter { !it.isDirectory }
        .filter { it.name.startsWith(COMMONS_PREFIX) }
        .filter { it.name.endsWith(COMMONS_SUFFIX_AROUND) || it.name.endsWith(COMMONS_SUFFIX_TENANT) }
        .map { it.name }
        .map { it.substringAfter(COMMONS_PREFIX) }
        .map { it.substringBefore(COMMONS_SUFFIX_TENANT) }
        .map { it.substringBefore(COMMONS_SUFFIX_AROUND) }
        .map { "${it}()" }
    variants.addAll(variantMethods)
    return variants.filter { it.startsWith(last) }
}

public fun getLepFolder(project: Project, place: PsiElement): VirtualFile? {
    val currentFile = place.originalFile.virtualFile
    val configRootDir = VfsUtil.findFile(File(project.getLinkedConfigRootDir()).toPath(), false) ?: return null
    val pathToLepFolder = listOf("lep", "tenant", "microservice", "lep")

    var parent = currentFile
    val path = LinkedList<VirtualFile>()
    while(parent?.path != configRootDir.path && parent?.parent != null) {
        parent = parent.parent
        if (parent != null) {
            path.add(parent)
        }
    }

    val index = path.size - pathToLepFolder.size
    if (path.size <= index || index < 0) {
        return null
    }

    return path.get(index)
}
