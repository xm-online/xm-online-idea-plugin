package com.icthh.xm.actions.deploy

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.actions.permission.GitContentProvider
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.settings.UpdateMode
import com.icthh.xm.service.*
import com.icthh.xm.service.filechanges.ChangesFiles
import com.icthh.xm.utils.Icons
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
import java.awt.Dimension
import java.io.File

class FileListDialog(project: Project, val changes: ChangesFiles): VaadinDialog(
    project = project, viewName = "file-list", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {
    lateinit var mainComponent: HasComponents

    override fun component(): Component {
        val ui = UI.getCurrent()
        val panel = Panel().apply {
            setSizeFull()
            addChild(mainComponent(ui))
        }
        mainComponent = panel
        return panel
    }

    private fun mainComponent(ui: UI): VerticalLayout {
        return VerticalLayout().apply {
            setSizeUndefined()
            setWidth("100%")

            changes.changesFiles.ifEmpty {
                label {
                    caption = "Already up to update"
                }
            }

            val ignoredFiles: MutableSet<String> = project.getSettings().selected()?.ignoredFiles ?: HashSet()
            changes.changesFiles.asSequence()
                .filter { it in changes.editedInThisIteration && !changes.isForceUpdate }
                .filterNot { it in ignoredFiles }
                .forEach { fileName ->
                    renderFileInformationLine(fileName, ui)
                }
            changes.changesFiles.asSequence()
                .filterNot { it in changes.editedInThisIteration && !changes.isForceUpdate }
                .filterNot { it in ignoredFiles }
                .forEach { fileName ->
                    renderFileInformationLine(fileName, ui)
                }
            ignoredFiles.asSequence()
                .filter { it in changes.changesFiles }
                .filterNot { changes.isForceUpdate }.forEach { fileName ->
                    val line = HorizontalLayout().apply {
                        label {
                            html("""<span><strike>${fileName}</strike></span>""")
                        }
                    }

                val actions = HorizontalLayout().apply {
                    link(" remove from ignored") {
                        addLayoutClickListener {
                            ignoredFiles.remove(fileName)
                            mainComponent.removeAllComponents()
                            mainComponent.addChild(mainComponent(ui))
                        }
                    }
                }
                actions.isVisible = !changes.isForceUpdate

                val fileLine = HorizontalLayout()
                fileLine.addComponents(line, actions)
                this.addComponent(fileLine)
            }

        }
    }

    private fun VerticalLayout.renderFileInformationLine(fileName: String, ui: UI) {
        val warning = Label("${WARNING.html} File changed", HTML)
        warning.isVisible = false

        val line = HorizontalLayout().apply {
            link { caption = fileName }
        }

        val actions = HorizontalLayout().apply {
            link(" ignore changes") {
                addLayoutClickListener {
                    project.getSettings().selected()?.ignoredFiles?.add(fileName)
                    mainComponent.removeAllComponents()
                    mainComponent.addChild(mainComponent(ui))
                }
            }
        }
        actions.isVisible = !changes.isForceUpdate

        val fileLine = HorizontalLayout()
        fileLine.addComponents(line, actions)
        this.addComponent(fileLine)

        line.addComponent(warning)
        val path = project.configPathToRealPath(fileName)
        val virtualFile = VfsUtil.findFile(File(path).toPath(), false)
        if (virtualFile != null) {
            markFileAsChanged(warning, line, fileName, virtualFile, ui)
        }
        line.apply {
            if (changes.toDelete.contains(fileName)) {
                label {
                    description = "It's file was deleted"
                    html(TRASH.html)
                }
            }
            if (changes.editedInThisIteration.contains(fileName)) {
                addEditIcon(line)
            }
            onLayoutClick {
                if (it.clickedComponent == null || !(it.clickedComponent is Link)) {
                    return@onLayoutClick
                }
                getApplication().invokeLater({
                    val psiFile = virtualFile?.toPsiFile(project)
                    psiFile?.navigate(true)
                }, ModalityState.stateForComponent(rootPane))
            }
        }
    }

    private fun markFileAsChanged(
        warning: Label,
        line: HorizontalLayout,
        configPath: String,
        virtualFile: VirtualFile,
        ui: UI
    ) {
        val fileName = virtualFile.path
        getApplication().executeOnPooledThread {
            val configService = project.getExternalConfigService()
            val settings = project.getSettings()?.selected() ?: return@executeOnPooledThread
            val config = configService.getConfigFileIfExists(project, settings, virtualFile.getConfigRelatedPath(project), null)

            if (config == null) {
                ui.access {
                    removeEditIcon(line)
                    line.apply {
                        label{
                            description = "It's new file"
                            html(Icons.FIBER_NEW)
                        }
                    }
                }
                return@executeOnPooledThread
            }

            val content = virtualFile.inputStream.use{it.readTextAndClose()}
            if (!config.equals(content)){
                ui.access {
                    removeEditIcon(line)
                    addEditIcon(line)
                }
            }

            if (!config.equals(content) && wasChanged(config, settings, fileName)) {
                ui.access {
                    removeEditIcon(line)
                    warning.isVisible = true

                    line.apply {
                        onLayoutClick {
                            if (it.clickedComponent == null || !(it.clickedComponent is Link)) {
                                return@onLayoutClick
                            }

                            getApplication().invokeLater({
                                showDiffDialog("File difference", config, configPath, fileName, project, virtualFile)
                            }, ModalityState.stateForComponent(rootPane))
                        }
                    }

                }
            }

        }
    }

    private fun addEditIcon(line: HorizontalLayout) {
        line.apply {
            label {
                description = "It's file edited in you version after last update"
                html(PENCIL.html)
                id = "EDIT"
            }
        }
    }

    private fun wasChanged(
        config: String?,
        settings: EnvironmentSettings,
        fileName: String
    ): Boolean {
        if (settings.updateMode.isGitMode) {
            var branchName = GitRevisionNumber.HEAD.rev
            if (settings.updateMode == UpdateMode.GIT_BRANCH_DIFFERENCE) {
                branchName = settings.branchName
            }
            val content = GitContentProvider(project, branchName).getFileContent(fileName)
            return (!sha256Hex(config).equals(sha256Hex(content)))
        } else {
            return (!sha256Hex(config).equals(settings.editedFiles.get(fileName)?.sha256)) && settings.editedFiles.isNotEmpty()
        }
    }

    private fun removeEditIcon(line: HorizontalLayout) {
        line.filter { it.id == "EDIT" }.forEach { line.removeComponent(it) }
    }

}
