package com.icthh.xm

import com.icthh.xm.extensions.entityspec.isEntitySpecification
import com.icthh.xm.extensions.entityspec.xmEntitySpecService
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.updateSymlinkToLep
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent


class ProjectStartupActivity: StartupActivity {
    override fun runActivity(project: Project) {
        logger.info("runActivity")
        project.updateSymlinkToLep()
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent?>) {
                events.forEach {
                    it ?: return@forEach
                    if (it.path.startsWith(project.getConfigRootDir()).isTrue()
                        && it.path.contains("xmentityspec")
                        && it.file.isEntitySpecification()
                    ) {
                        updateEntitySpec(it)
                    }
                }
            }

            private fun updateEntitySpec(it: VFileEvent) {
                logger.info("VFS_CHANGES ${it.path}")
                val file = it.file ?: return
                val xmEntitySpecService = project.xmEntitySpecService
                if (!file.exists().isTrue()) {
                    xmEntitySpecService.removeEntityFile(file);
                    return
                }
                if (it is VFileCreateEvent) {
                    xmEntitySpecService.invalidate(file.getTenantName(project))
                    return
                }
                xmEntitySpecService.updateEntityFile(file);
            }

        })
    }

}
