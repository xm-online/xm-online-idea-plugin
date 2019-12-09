package com.icthh.xm.editors

import com.github.mvysny.karibudsl.v8.expandRatio
import com.github.mvysny.karibudsl.v8.grid
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.domain.permission.dto.Privilege
import com.icthh.xm.domain.permission.dto.RoleDTO
import com.icthh.xm.domain.permission.dto.same
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.vaadin.data.provider.DataProvider
import com.vaadin.ui.CheckBox
import com.vaadin.ui.Component
import com.vaadin.ui.Grid
import com.vaadin.ui.VerticalLayout
import java.awt.Dimension
import java.awt.Toolkit

class PermissionDialog(project: Project, val currentFile: VirtualFile, val diffContent: String, val branchName: String) :
    VaadinEditor(project, "permission-diff", "Permission difference", getDialogSize()) {

    var documentAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun viewComponent(): Component {
        val onBranchPermissions = toMatrix(diffContent)
        val tenantRoleService = TenantRoleService(diffContent, project) {writeAction(it)}
        val roles = tenantRoleService.getAllRoles().map { it.roleKey to tenantRoleService.getRole(it.roleKey) }.toMap()
        val currentPermissions = roles.flatMap { toPermissions(it.value) }

        val otherPrivileges = onBranchPermissions.map { it.toPrivilege() to it }.toMap()
        val thisPrivileges = currentPermissions.map { it.toPrivilege() to it }.toMap()

        val allPrivileges = ArrayList<Privilege>()
            .union(otherPrivileges.keys)
            .union(thisPrivileges.keys)
            .toSet()

        val diffPermissions = ArrayList<ComparePermissionDiff>()
        allPrivileges.filter { thisPrivileges[it].same(otherPrivileges[it]) }.forEach {
            val branch = otherPrivileges[it]
            val current = thisPrivileges[it]
            val permission = ComparePermissionDiff(it.privilegeKey, it.msName, branch, current, roles[it.role]!!)
            diffPermissions.add(permission)
        }

        lateinit var grid: Grid<ComparePermissionDiff>
        return VerticalLayout().apply {
            setSizeFull()

            grid = grid {
                expandRatio = 1f;
                setColumnReorderingAllowed(true);
                setSelectionMode(Grid.SelectionMode.NONE)
                setSizeFull()

                val role = addColumn{ it.roleDto.roleKey }
                role.setCaption("Role")
                role.isHidable = true

                val privilegeKey = addColumn{ it.privilegeKey }
                privilegeKey.setCaption("Privilege key")
                privilegeKey.isHidable = true

                val msName = addColumn{ it.msName }
                msName.setCaption("MS name")
                msName.isHidable = true
                msName.isHidden = true

                val enabledOnBranch = addColumn{
                    // it.branchPermissionDTO.enabled
                }
                enabledOnBranch.setCaption("Enabled on ${branchName} ")
                enabledOnBranch.isHidable = true

                val spelOnBranch = addColumn{
                    // it.branchPermissionDTO.resourceCondition
                }
                spelOnBranch.setCaption("Spel on ${branchName} ")
                spelOnBranch.isHidable = true

                val column = addComponentColumn { column ->
                    //val permission = column.thisBranch
                    CheckBox().apply {
                        //value = permission.roles.contains(role)
                        addValueChangeListener {
                            if (it.value) {
                                //permission.roles.add(role)
                            } else {
                                //permission.roles.remove(role)
                            }
                            ApplicationManager.getApplication().executeOnPooledThread {
                                //tenantRoleService.updateRoleMatrix(roles[it])
                            }
                        }
                    }
                }
                column.isHidable = true

                setDataProvider( DataProvider.fromCallbacks(
                    { query ->
                        val permission = diffPermissions
                        val start = query.getOffset()
                        val end = Math.min(query.getLimit() + start, permission.size)
                        permission.toList().subList(start, end).stream()
                    },
                    { diffPermissions.size }
                ))
            }


        }
    }


    fun writeAction(update: () -> Unit) {
        if (documentAlarm.isDisposed) {
            return
        }

        documentAlarm.cancelAllRequests()
        documentAlarm.addRequest({
            ApplicationManager.getApplication().runWriteAction {
                update.invoke()
            }
        }, 50)
    }

    private fun toMatrix(diffContent: String): List<PermissionDTO> {
        val tenantRoleService = TenantRoleService(diffContent, project) {writeAction(it)}
        return tenantRoleService.getAllRoles().flatMap { toPermissions(it) }
    }

    private fun toPermissions(roleDTO: RoleDTO): List<PermissionDTO> {
        return roleDTO.permissions?.toList() ?: ArrayList()
    }
}

fun getDialogSize(): Dimension {
    val screenSize = Toolkit.getDefaultToolkit().getScreenSize()
    return Dimension(600, screenSize.height * 8 / 10)
}

data class ComparePermissionDiff(
    val privilegeKey: String,
    val msName: String,
    val branchPermissionDTO: PermissionDTO?,
    val thisPermissionDTO: PermissionDTO?,
    val roleDto: RoleDTO
) {

}
