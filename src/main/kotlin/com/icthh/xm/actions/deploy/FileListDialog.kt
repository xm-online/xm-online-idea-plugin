package com.icthh.xm.actions.deploy

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.toPsiFile
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.vaadin.ui.Component
import com.vaadin.ui.Panel
import com.vaadin.ui.VerticalLayout
import java.awt.Dimension
import java.io.File

class FileListDialog(project: Project, val fields: List<String>): VaadinDialog(
    project = project, viewName = "file-list", dialogTitle = "File to update",
    dimension = Dimension(1024, 300)
) {
    override fun component(): Component {
        return Panel().apply {
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
                    horizontalLayout {
                        val link = link {
                            caption = "/config" + fileName.substring(configRootDir.length)
                        }
                        onLayoutClick {
                            val component = it.clickedComponent ?: return@onLayoutClick
                            if (link == component) {
                                getApplication().invokeLater ({
                                    VfsUtil.findFile(File(fileName).toPath(), false)?.toPsiFile(project)?.navigate(true)
                                }, ModalityState.stateForComponent(rootPane))
                            }
                        }
                    }
                }
            })
        }
    }

}
