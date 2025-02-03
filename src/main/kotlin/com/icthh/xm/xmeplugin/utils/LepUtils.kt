package com.icthh.xm.xmeplugin.utils

import addToGit
import com.icthh.xm.xmeplugin.extensions.xmentityspec.getEntityRootFolder
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import createProjectFile
import org.jetbrains.yaml.psi.YAMLValue
import java.io.File
import java.nio.file.Path
import java.util.*

fun translateToLepConvention(xmEntitySpecKey: String): String {
    Objects.requireNonNull(xmEntitySpecKey, "xmEntitySpecKey can't be null")
    return xmEntitySpecKey.replace("-".toRegex(), "_").replace("\\.".toRegex(), "\\$")
}

fun lepCreationTip(
    element: YAMLValue,
    holder: ProblemsHolder,
    lepKey: String,
    lepSegments: List<String>,
    message: (String) -> String,
    lepGroup: String
): Boolean {
    val lepDirectory = element.getEntityRootFolder() + lepGroup
    val exists = buildLepVariants(lepKey, lepSegments).any {
        VfsUtil.findFile(Path.of(lepDirectory + it), false) != null
    }

    if (exists) {
        return true
    }
    val lepName = lepKey + "$$" + lepSegments.joinToString(separator = "$$") + ".groovy"
    val description = "You can override logic by creating LEP"
    holder.registerProblem(element, description, INFORMATION, object : LocalQuickFix {
        override fun getFamilyName() = message.invoke(lepName)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            createLepFile(lepDirectory, lepName, project)
        }
    })
    return false
}

fun buildLepVariants(lepKey: String, segments: List<String>): List<String> {
    val legacyLepPath: String = lepKey + "$$" + segments.map { translateToLepConvention(it) }.joinToString(separator = "$$")
    val lepPath: String = lepKey + "$$" + segments.joinToString(separator = "$$")
    return listOf(
        "$legacyLepPath$\$tenant", "$legacyLepPath$\$around",
        "$lepPath$\$tenant", "$lepPath$\$around",
        legacyLepPath, lepPath
    )
}

private fun createLepFile(
    lepDirectory: String,
    lepName: String,
    project: Project,
    pathToLep: String = ""
) {
    createProjectFile(lepDirectory + pathToLep, lepName, project, "return null")
}

