package com.icthh.xm.actions.permission

import com.icthh.xm.service.getRepository
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.util.GitFileUtils
import java.nio.charset.StandardCharsets.UTF_8

class GitContentProvider(
    val project: Project,
    val branch: String
) {
    fun getFileContent(filePath: String): String {
        val repository = project.getRepository() ?: return ""
        val relativePath = filePath.substringAfter(repository.root.path)
        try {
            val fileContent = GitFileUtils.getFileContent(project, repository.root, branch, "." + relativePath)
            return String(fileContent, UTF_8)
        } catch (e: VcsException) {
            return ""
        }
    }
}
