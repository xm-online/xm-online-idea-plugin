package com.icthh.xm.actions.deploy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.actions.WebDialog
import com.icthh.xm.actions.permission.GitContentProvider
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.UpdateMode
import com.icthh.xm.service.*
import com.icthh.xm.service.filechanges.ChangesFiles
import com.icthh.xm.utils.Icons
import com.icthh.xm.utils.logger
import com.icthh.xm.utils.readTextAndClose
import com.icthh.xm.utils.showDiffDialog
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.vaadin.icons.VaadinIcons.*
import com.vaadin.shared.ui.ContentMode.HTML
import com.vaadin.ui.*
import git4idea.GitRevisionNumber
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import java.awt.Dimension
import java.io.File

class WebFileListDialog(project: Project, val changes: ChangesFiles): WebDialog(
    project = project, viewName = "file-list-dialog", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {
    lateinit var mainComponent: HasComponents

    val mapper = jacksonObjectMapper()
    val conflictedFilesContent = HashMap<String, String>()

    override fun callbacks(): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                logger.info("component ready")
                val calculateFileChanges = calculateFileChanges()
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "isForceUpdate" to changes.isForceUpdate,
                    "files" to calculateFileChanges
                )))
                calculateFileChanges.forEach {
                    markFileAsChanged(it)
                }
            }
        )
    }

    private fun calculateFileChanges(): List<FileChange> {
        val fileChanges = ArrayList<FileChange>()
        val ignoredFiles: MutableSet<String> = project.getSettings().selected()?.ignoredFiles ?: HashSet()
        changes.changesFiles.asSequence().forEach {
            val file = FileChange(it, project.configPathToRealPath(it))
            file.editedInThisIteration = it in changes.editedInThisIteration
            file.ignoredFile = it in ignoredFiles
            file.toDelete = it in changes.toDelete
        }
        return fileChanges
    }

    private fun openFile(fileName: String) {
        val path = project.configPathToRealPath(fileName)
        val virtualFile = VfsUtil.findFile(File(path).toPath(), false)
        getApplication().invokeLater({
            val psiFile = virtualFile?.toPsiFile(project)
            psiFile?.navigate(true)
        }, ModalityState.stateForComponent(rootPane))
    }

    private fun markFileAsChanged(file: FileChange) {
        val virtualFile = VfsUtil.findFile(File(file.path).toPath(), false)
        virtualFile ?: return

        val fileName = virtualFile.path
        getApplication().executeOnPooledThread {
            val configService = project.getExternalConfigService()
            val settings = project.getSettings()?.selected() ?: return@executeOnPooledThread
            val config = configService.getConfigFileIfExists(project, settings, virtualFile.getConfigRelatedPath(project), null)

            if (config == null) {
                file.editedInThisIteration = false
                file.isNewFile = true
            } else {
                val content = virtualFile.inputStream.use { it.readTextAndClose() }
                file.editedInThisIteration = !config.equals(content)

                if (!config.equals(content) && wasChanged(config, settings, fileName)) {
                    file.editedInThisIteration = false
                    file.isConflict = true
                    conflictedFilesContent.put(fileName, content)
                }
            }
        }
    }

    private fun openDiffDialog(
        config: String,
        file: FileChange,
        virtualFilePath: String,
        virtualFile: VirtualFile
    ) {
        getApplication().invokeLater({
            showDiffDialog("File difference", config, file.fileName, virtualFilePath, project, virtualFile)
        }, ModalityState.stateForComponent(rootPane))
    }

    private fun wasChanged(config: String?, settings: EnvironmentSettings, fileName: String ): Boolean {
        if (settings.updateMode.isGitMode) {
            var branchName = GitRevisionNumber.HEAD.rev
            if (settings.updateMode == UpdateMode.GIT_BRANCH_DIFFERENCE) {
                branchName = settings.branchName
            }
            val content = GitContentProvider(project, branchName).getFileContent(fileName)
            return (!sha256Hex(config).equals(sha256Hex(content))) && wasEditedFromLastUpdate(config, settings, fileName)
        } else {
            return wasEditedFromLastUpdate(config, settings, fileName) && settings.editedFiles.isNotEmpty()
        }
    }

    private fun wasEditedFromLastUpdate(
        config: String?,
        settings: EnvironmentSettings,
        fileName: String
    ) = (!sha256Hex(config).equals(settings.editedFiles.get(fileName)?.sha256))

    private fun removeEditIcon(line: HorizontalLayout) {
        line.filter { it.id == "EDIT" }.forEach { line.removeComponent(it) }
    }

}

data class FileChange(
    val fileName: String,
    val path: String,
    var editedInThisIteration: Boolean? = null,
    var isNewFile: Boolean? = null,
    var changesFile: Boolean? = null,
    var ignoredFile: Boolean? = null,
    var toDelete: Boolean? = null,
    var isConflict: Boolean? = null
)
