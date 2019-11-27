package com.icthh.xm.actions.deploy

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.service.*
import com.icthh.xm.utils.showDiffDialog
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.vaadin.icons.VaadinIcons.WARNING
import com.vaadin.shared.ui.ContentMode.HTML
import com.vaadin.ui.*
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import java.awt.Dimension
import java.io.File

class FileListDialog(project: Project, val fields: List<String>): VaadinDialog(
    project = project, viewName = "file-list", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {
    override fun component(): Component {
        val ui = UI.getCurrent()
        val panel = Panel().apply {
            setSizeFull()

            addChild(VerticalLayout().apply {
                setSizeUndefined()

                fields.ifEmpty {
                    label {
                        caption = "Already up to update"
                    }
                }
                val configRootDir = project.getConfigRootDir()
                fields.forEach { fileName ->
                    val warning = Label("${WARNING.getHtml()} File changed", HTML)
                    warning.isVisible = false

                    val configPath = "/config" + fileName.substring(configRootDir.length)
                    val line = horizontalLayout {
                        link { caption = configPath }
                    }
                    line.addComponent(warning)
                    val virtualFile = VfsUtil.findFile(File(fileName).toPath(), false)
                    if (virtualFile != null) {
                        markFileAsChanged(warning, line, configPath, virtualFile, ui)
                    }
                    line.apply {
                        onLayoutClick {
                            if (it.clickedComponent == null || !(it.clickedComponent is Link)) {
                                return@onLayoutClick
                            }
                            getApplication().invokeLater ({
                                val psiFile = virtualFile?.toPsiFile(project)
                                psiFile?.navigate(true)
                            }, ModalityState.stateForComponent(rootPane))
                        }
                    }

                }
            })
        }
        return panel
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
            val config = configService.getConfigFile(settings, virtualFile.getConfigRelatedPath(project))

            val sha256Hex = settings.editedFiles.get(virtualFile.path)?.sha256
            if (!sha256Hex(config).equals(sha256Hex)) {
                ui.access {
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

}
