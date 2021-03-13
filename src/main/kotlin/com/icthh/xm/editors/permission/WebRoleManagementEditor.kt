package com.icthh.xm.editors.permission

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.actions.BrowserPipe
import com.icthh.xm.domain.permission.dto.RoleDTO
import com.icthh.xm.editors.WebEditor
import com.icthh.xm.extensions.entityspec.isEntitySpecification
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.log
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.jcef.JBCefBrowser


class WebRoleManagementEditor(val currentProject: Project, val currentFile: VirtualFile) :
    WebEditor(currentProject, "role-management", "Role management") {

    val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val connection = project.messageBus.connect()

    fun writeAction(update: () -> Unit) {
        invokeOnUiThread {
            getApplication().runWriteAction {
                update.invoke()
            }
        }
    }

    override fun getFile(): VirtualFile {
        return currentFile
    }

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject) { writeAction(it) }
        val allRoles = tenantRoleService.getAllRoles().map {
            tenantRoleService.getRole(it.roleKey)
        }
        val msNames = allRoles.firstOrNull()?.permissions.orEmpty().map { it.msName }.toSet()

        return listOf(
            BrowserCallback("componentReady") { body, pipe ->
                listenFileChanges(tenantRoleService, pipe)
                pipe.post(
                    "initData", mapper.writeValueAsString(
                        mapOf(
                            "msNames" to msNames,
                            "allRoles" to allRoles
                        )
                    )
                )
            },
            BrowserCallback("updateRole") { body, pipe ->
                val role = mapper.readValue<RoleDTO>(body)
                with(role) {
                    log.info("update permission")
                    tenantRoleService.updateRole(role)
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
        val allRoles = tenantRoleService.getAllRoles().map {
            tenantRoleService.getRole(it.roleKey)
        }
        val msNames = allRoles.firstOrNull()?.permissions.orEmpty().map { it.msName }.toSet()
        pipe.post(
            "updateData", mapper.writeValueAsString(
                mapOf(
                    "msNames" to msNames,
                    "allRoles" to allRoles
                )
            )
        )
    }

    override fun dispose() {
        super.dispose()
        connection.dispose();
    }

}

