package com.icthh.xm.actions.deploy

import com.icthh.xm.utils.getSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.util.io.inputStream
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import org.apache.commons.codec.digest.DigestUtils
import java.nio.file.Files.walk
import java.nio.file.Paths


class TrackChanges() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val settings = project.getSettings()?.selected()
        settings ?: return
        settings.trackChanges = true

        val basePath = project.basePath + "/config"
        walk(Paths.get(basePath)).use{
            it.filter { !it.isDirectory() }.forEach{
                settings.editedFiles.put(it.systemIndependentPath, DigestUtils.sha256Hex(it.inputStream()))
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project

        val settings = project?.getSettings()?.selected()
        anActionEvent.presentation.isEnabled = project != null && settings != null
        anActionEvent.presentation.isVisible = settings?.trackChanges?.not() ?: true

        if (settings != null && settings.trackChanges) {
            val vFile = anActionEvent.getData(PlatformDataKeys.VIRTUAL_FILE)
            val editedFiles = settings.editedFiles
            if (vFile != null && !vFile.isDirectory && !editedFiles.contains(vFile.path)) {
                editedFiles.put(vFile.path, DigestUtils.sha256Hex(vFile.inputStream))
            }
        }

    }
}
