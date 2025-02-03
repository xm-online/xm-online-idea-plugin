package com.icthh.xm.xmeplugin.webview

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.domain.ChangesFiles
import com.icthh.xm.xmeplugin.services.configRestService
import com.icthh.xm.xmeplugin.services.settings.EnvironmentSettings
import com.icthh.xm.xmeplugin.services.settings.UpdateMode
import com.icthh.xm.xmeplugin.utils.doAsync
import com.icthh.xm.xmeplugin.utils.getSettings
import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.utils.log
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import configPathToRealPath
import getFileContent
import normalizedPath
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import readTextAndClose
import toRelatedPath
import java.awt.Dimension
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentHashMap

class WebFileListDialog(project: Project, val changes: ChangesFiles): WebDialog(
    project = project, viewName = "file-list-dialog", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {

    private val mapper = jacksonObjectMapper()
    private val configFilesContent = ConcurrentHashMap<String, String>()

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        val ignoredFiles: MutableSet<String> = project.getSettings().selected()?.ignoredFiles ?: HashSet()
        val calculateFileChanges = calculateFileChanges()
        return listOf(
            BrowserCallback("componentReady") { _, pipe ->
                log.info("component ready")
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "isForceUpdate" to changes.isForceUpdate,
                    "changes" to calculateFileChanges
                )))
                calculateFileChanges.forEach {
                    checkFileAsChanged(it) {
                        invokeOnUiThread {
                            pipe.post("updateFile", mapper.writeValueAsString(it))
                        }
                    }
                }
            },
            BrowserCallback("updateFile") { body, _ ->
                log.info("updateFiled")
                val fileChange = mapper.readValue<FileChange>(body)
                if (fileChange.ignoredFile.isTrue) {
                    ignoredFiles.add(fileChange.fileName)
                } else {
                    ignoredFiles.remove(fileChange.fileName)
                }
                project.scheduleSave()

                log.info("file updated")
            },
            BrowserCallback("navigate") { body, _ ->
                log.info("navigate")
                val fileChange = mapper.readValue<FileChange>(body)
                val path = fileChange.path
                val fileName = fileChange.fileName
                project.doAsync {
                    val virtualFile = VfsUtil.findFile(File(path).toPath(), false) ?: return@doAsync
                    showDiffDialog(
                        "File difference", configFilesContent.get(path) ?: "",
                        path, fileName, project, virtualFile
                    )
                }
            }
        )
    }

    fun showDiffDialog(windowTitle: String, content: String, title1: String,
                       title2: String, project: Project, file: VirtualFile
    ) {
        val content1 = DiffContentFactory.getInstance().create(content)
        val content2 = DiffContentFactory.getInstance().create(project, file)
        val request = SimpleDiffRequest(windowTitle, content1, content2, title1, title2)
        invokeOnUiThread {
            DiffManager.getInstance().showDiff(project, request)
        }
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
        project.doAsync {
            val fsFile = File(file.path)
            val absolutePath = fsFile.normalizedPath
            val virtualFile = VfsUtil.findFile(fsFile.toPath(), false) ?: return@doAsync

            val fileName = virtualFile.path
            val configService = project.configRestService
            val settings = project.getSettings().selected() ?: return@doAsync
            val config = configService.getConfigFileIfExists(project, settings, project.toRelatedPath(absolutePath), null)

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
                configFilesContent[fileName] = config
            }
            onChange.invoke(file)
        }
    }

    private fun wasChanged(config: String?, settings: EnvironmentSettings, fileName: String): Boolean {
            var branchName = "HEAD"
            if (settings.updateMode == UpdateMode.GIT_BRANCH_DIFFERENCE) {
                branchName = settings.branchName
            }
            val content = project.getFileContent(branchName, fileName) ?: "".toByteArray()
            return (!sha256Hex(config?.toByteArray(UTF_8) ?: "".toByteArray()).equals(sha256Hex(content)))
                    && wasEditedFromLastUpdate(config ?: "", settings, project.toRelatedPath(fileName))
    }

    private fun wasEditedFromLastUpdate(
        config: String,
        settings: EnvironmentSettings,
        fileName: String
    ) = (!sha256Hex(config.trim()).equals(settings.lastChangedState.get(fileName)))

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
