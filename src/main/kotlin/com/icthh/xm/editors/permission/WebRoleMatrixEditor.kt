package com.icthh.xm.editors.permission

import com.fasterxml.jackson.databind.DeserializationFeature.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.BrowserPipe
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.domain.permission.dto.PermissionType
import com.icthh.xm.domain.permission.dto.RoleMatrixDTO
import com.icthh.xm.editors.WebEditor
import com.icthh.xm.extensions.entityspec.isEntitySpecification
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.log
import com.icthh.xm.utils.logger
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.jcef.JBCefBrowser
import java.util.*


class WebRoleMatrixEditor(val currentProject: Project, val currentFile: VirtualFile): WebEditor(currentProject, "role-matrix", "Role matrix") {

    val mapper = jacksonObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)
    val connection = project.messageBus.connect()

    fun writeAction(update: () -> Unit) {
        invokeOnUiThread {
            getApplication().runWriteAction {
                update.invoke()
            }
        }
    }

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject) { writeAction(it) }
        val roleMatrix = tenantRoleService.getRoleMatrix()
        val msNames = roleMatrix.permissions.map { it.msName }.toSet()

        return listOf(
            BrowserCallback("componentReady") {body, pipe ->
                listenFileChanges(tenantRoleService, pipe)
                pipe.post("initData", mapper.writeValueAsString(mapOf(
                    "msNames" to msNames,
                    "roleMatrix" to roleMatrix
                )))
            },
            BrowserCallback("updateMatrix") {body, pipe ->
                logger.info("envsUpdated")
                val roleMatrix = mapper.readValue<RoleMatrixDTO>(body)
                with(roleMatrix) {
                    log.info("update permission")
                    tenantRoleService.updateRoleMatrix(roleMatrix)
                }
            }
        )
    }

    private fun listenFileChanges(
        tenantRoleService: TenantRoleService,
        pipe: BrowserPipe
    ) {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent?>) {
                events.forEach {
                    it ?: return@forEach
                    if (it.path.startsWith(project.getConfigRootDir())
                            .isTrue() && (it.file.isEntitySpecification() || it.path.endsWith("permissions.yml"))
                    ) {
                        getApplication().executeOnPooledThread {
                            log.info("entity updated")
                            updateData(tenantRoleService, pipe)
                        }
                    }
                }
            }
        })
    }

    private fun updateData(
        tenantRoleService: TenantRoleService,
        pipe: BrowserPipe
    ) {
        val roleMatrix = tenantRoleService.getRoleMatrix()
        val msNames = roleMatrix.permissions.map { it.msName }.toSet()

        pipe.post(
            "updateData", mapper.writeValueAsString(
                mapOf(
                    "msNames" to msNames,
                    "roleMatrix" to roleMatrix
                )
            )
        )
    }

    override fun getFile(): VirtualFile {
        return currentFile
    }

}
