package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.utils.isConfigProject
import com.icthh.xm.xmeplugin.utils.log
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*


class YamlAsyncFileListener : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val relevantEvents = events.filter { event ->
            when (event) {
                // TODO add content change events and listen xme-plugin.yml events and reindex all on change =))
                is VFileCreateEvent, is VFileDeleteEvent, is VFileMoveEvent -> true
                is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME
                else -> false
            }
        }

        if (relevantEvents.isEmpty()) {
            return null
        }

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                relevantEvents.forEach { event ->
                    getProject(event.file)?.handleEvent(event)
                }
            }
        }
    }

    private fun Project.handleEvent(event: VFileEvent) {
        if (!isConfigProject()) {
            return
        }

        log.info("After VFS change: ${event.javaClass.simpleName} on ${event.file?.path}")

        val file = event.file ?: return
        when(event) {
            is VFileCreateEvent -> xmePluginSpecService.fileAdded(file)
            is VFileDeleteEvent -> xmePluginSpecService.fileDeleted(file.path)
            is VFileMoveEvent -> {
                xmePluginSpecService.fileDeleted(event.oldPath)
                xmePluginSpecService.fileAdded(file)
            }
            is VFilePropertyChangeEvent -> {
                if (event.propertyName == VirtualFile.PROP_NAME) {
                    xmePluginSpecService.fileDeleted(event.oldPath)
                    xmePluginSpecService.fileAdded(file)
                }
            }
        }
    }

    private fun getProject(file: VirtualFile?): Project? {
        if (file == null) {
            return null
        }
        return ProjectManager.getInstance().openProjects
            .filter { it.isConfigProject() }
            .firstOrNull { ProjectFileIndex.getInstance(it).isInContent(file) }
    }


}

