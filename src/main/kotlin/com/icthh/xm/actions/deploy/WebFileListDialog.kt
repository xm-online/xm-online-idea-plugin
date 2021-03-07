package com.icthh.xm.actions.deploy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.WebDialog
import com.icthh.xm.actions.permission.GitContentProvider
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.UpdateMode
import com.icthh.xm.service.*
import com.icthh.xm.service.filechanges.ChangesFiles
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.logger
import com.icthh.xm.utils.readTextAndClose
import com.icthh.xm.utils.showDiffDialog
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.jcef.JBCefBrowser
import git4idea.GitRevisionNumber
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.awt.Dimension
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class WebFileListDialog(project: Project, val changes: ChangesFiles): WebDialog(
    project = project, viewName = "file-list-dialog", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {

    val mapper = jacksonObjectMapper()
    val configFilesContent = ConcurrentHashMap<String, String>()

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        val modalState = ModalityState.current()
        val ignoredFiles: MutableSet<String> = project.getSettings().selected()?.ignoredFiles ?: HashSet()
        val calculateFileChanges = calculateFileChanges()
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                logger.info("component ready")
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "isForceUpdate" to changes.isForceUpdate,
                    "changes" to calculateFileChanges
                )))
                calculateFileChanges.forEach {
                    checkFileAsChanged(it) {
                        pipe.post("updateFile", mapper.writeValueAsString(it))
                    }
                }
            },
            BrowserCallback("updateFile") { body, pipe ->
                logger.info("updateFiled")
                val fileChange = mapper.readValue<FileChange>(body)
                if (fileChange.ignoredFile.isTrue()) {
                    ignoredFiles.add(fileChange.fileName)
                } else {
                    ignoredFiles.remove(fileChange.fileName)
                }
                invokeOnUiThread {
                    project.save()
                }

                logger.info("file updated")
            },
            BrowserCallback("navigate") { body, pipe ->
                logger.info("navigate")
                val fileChange = mapper.readValue<FileChange>(body)
                val path = fileChange.path
                val fileName = fileChange.fileName
                invokeOnUiThread {
                    val virtualFile = VfsUtil.findFile(File(path).toPath(), false) ?: return@invokeOnUiThread
                    showDiffDialog(
                        "File difference", configFilesContent.get(path) ?: "",
                        path, fileName, project, virtualFile
                    )
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
            fileChanges.add(file)
        }
        return fileChanges
    }

    private fun checkFileAsChanged(file: FileChange, onChange: (FileChange) -> Unit) {
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
                }
                configFilesContent.put(fileName, config)
            }
            onChange.invoke(file)
        }
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

}

@JsonIgnoreProperties(ignoreUnknown = true)
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
