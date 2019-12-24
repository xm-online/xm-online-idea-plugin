package com.icthh.xm.editors

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.actions.permission.GitContentProvider
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.domain.permission.dto.RoleDTO
import com.icthh.xm.domain.permission.dto.same
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.vaadin.data.provider.DataProvider
import com.vaadin.ui.*
import java.awt.Dimension
import java.awt.Toolkit
import java.util.*
import kotlin.collections.ArrayList

class PermissionDialog(project: Project,
                       val currentFile: VirtualFile,
                       val contentProvider: GitContentProvider,
                       val branchName: String) :
    VaadinDialog(project, "permission-diff", getDialogSize(), "Permission difference") {

    //var documentAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun component(): Component {
        val tenantName = currentFile.getTenantName(project)
        val onBranchPermissions = toMatrix(tenantName)
        val tenantRoleService = TenantRoleService(tenantName, project) //{writeAction(it)}
        val roles = tenantRoleService.getAllRoles().map { tenantRoleService.getRole(it.roleKey) }
        val currentPermissions = roles.flatMap { it.permissions ?: ArrayList() }

        val otherPrivileges = onBranchPermissions.map { it.toKey() to it }.toMap()
        val thisPrivileges = currentPermissions.map { it.toKey() to it }.toMap()

        val allPrivileges = ArrayList<PrivilegeItemKey>()
            .union(otherPrivileges.keys)
            .union(thisPrivileges.keys)
            .toSet()

        val diffPermissions = allPrivileges.filter { !thisPrivileges[it].same(otherPrivileges[it]) }.map {
            val permission = it
            val roleDTO = roles.first { it.roleKey == permission.role }
            ComparePermissionDiff(it, otherPrivileges[it], thisPrivileges[it], roleDTO)
        }

        lateinit var grid: Grid<ComparePermissionDiff>
        return Panel().apply {
            setSizeFull()

            verticalLayout {
                diffPermissions.forEach {
                    horizontalLayout {
                        setSizeFull()
                        permissionCart()
                        permissionCart()
                    }
                }
            }
        }
    }

    private fun @VaadinDsl HorizontalLayout.permissionCart() {
        verticalLayout {
            setSizeFull()
            
        }
    }

//    fun writeAction(update: () -> Unit) {
//        if (documentAlarm.isDisposed) {
//            return
//        }
//
//        documentAlarm.cancelAllRequests()
//        documentAlarm.addRequest({
//            ApplicationManager.getApplication().runWriteAction {
//                update.invoke()
//            }
//        }, 50)
//    }

    private fun toMatrix(tenantName: String): List<PermissionDTO> {
        val tenantRoleService = object: TenantRoleService(tenantName, project) {
            override fun getConfigContent(configPath: String): Optional<String> {
                val content = contentProvider.getFileContent(configPath)
                if (content.isBlank()) {
                    return Optional.of("---")
                }
                return Optional.of(content)
            }
        }
        return tenantRoleService.getAllRoles().flatMap { toPermissions(tenantRoleService, it) }
    }

    private fun toPermissions(tenantRoleService: TenantRoleService, roleDTO: RoleDTO): List<PermissionDTO> {
        return tenantRoleService.getRole(roleDTO.roleKey).permissions?.toList() ?: ArrayList()
    }
}

fun getDialogSize(): Dimension {
    val screenSize = Toolkit.getDefaultToolkit().getScreenSize()
    return Dimension(600, screenSize.height * 8 / 10)
}

data class ComparePermissionDiff(
    val privilege: PrivilegeItemKey,
    val currentBranch: PermissionDTO?,
    val otherBranch: PermissionDTO?,
    val origin: RoleDTO
)

data class PrivilegeItemKey(val privilegeKey: String, val msName: String, val role: String)

fun PermissionDTO.toKey() = PrivilegeItemKey(privilegeKey, msName, roleKey)

