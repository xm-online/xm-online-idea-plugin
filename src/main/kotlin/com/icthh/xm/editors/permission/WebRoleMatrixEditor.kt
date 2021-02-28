package com.icthh.xm.editors.permission

import com.fasterxml.jackson.databind.DeserializationFeature.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.domain.permission.dto.PermissionType
import com.icthh.xm.domain.permission.dto.RoleMatrixDTO
import com.icthh.xm.editors.WebEditor
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.icthh.xm.utils.log
import com.icthh.xm.utils.logger
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.util.*


class WebRoleMatrixEditor(val currentProject: Project, val currentFile: VirtualFile): WebEditor(currentProject, "role-matrix", "Role matrix") {

    val mapper = jacksonObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES)

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

    override fun getFile(): VirtualFile {
        return currentFile
    }

}
