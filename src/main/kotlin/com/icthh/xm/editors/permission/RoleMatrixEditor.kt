package com.icthh.xm.editors.permission

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.domain.permission.dto.RoleMatrixDTO
import com.icthh.xm.editors.VaadinEditor
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import com.vaadin.data.provider.DataProvider
import com.vaadin.server.Sizeable.Unit.PIXELS
import com.vaadin.ui.CheckBox
import com.vaadin.ui.Component
import com.vaadin.ui.Grid
import com.vaadin.ui.Grid.SelectionMode.NONE
import com.vaadin.ui.VerticalLayout


class RoleMatrixEditor(val currentProject: Project, val currentFile: VirtualFile): VaadinEditor(currentProject, "role-matrix", "Role matrix") {

    var documentAlarm = Alarm(SWING_THREAD, this)
    var selectedMs: String? = null
    var permissionToSearch: String? = null

    override fun viewComponent(): Component {

        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject)
        val roleMatrix = tenantRoleService.getRoleMatrix()
        val msNames = getPermission(roleMatrix).map { it.msName }.toSet()

        lateinit var grid: Grid<PermissionMatrixDTO>
        val rootLayout = VerticalLayout().apply {
            setSizeFull()

            horizontalLayout {
                comboBox<String> {
                    setItems(msNames)
                    addValueChangeListener {
                        selectedMs = it.value
                        grid.refresh()
                    }
                }
                textField {
                    setWidth(300f, PIXELS)
                    onEnterPressed {
                        permissionToSearch = it.value
                        grid.refresh()
                    }
                }
            }
            grid = grid {
                expandRatio = 1f;
                setColumnReorderingAllowed(true);
                setSelectionMode(NONE)
                setSizeFull()

                val privilegeKey = addColumn{ it.privilegeKey }
                privilegeKey.setCaption("Privilege key")

                val msName = addColumn{ it.msName }
                msName.setCaption("MS name")
                msName.isHidable = true

                roleMatrix.roles.forEach { role ->
                    val column = addComponentColumn { column ->
                        CheckBox().apply {
                            value = column.roles.contains(role)
                            addValueChangeListener {
                                if (it.value) {
                                    column.roles.add(role)
                                } else {
                                    column.roles.remove(role)
                                }
                                update {
                                    val mapper = jacksonObjectMapper()
                                    val roleMatrixDTO = mapper.readValue<RoleMatrixDTO>(mapper.writeValueAsBytes(roleMatrix))
                                    tenantRoleService.updateRoleMatrix(roleMatrixDTO)
                                }
                            }
                        }
                    }
                    column.isSortable = false
                    column.caption = role
                    column.isHidable = true
                }
                setDataProvider( DataProvider.fromCallbacks(
                    { query ->
                        val permission = getPermission(roleMatrix)
                        val start = query.getOffset()
                        val end = Math.min(query.getLimit() + start, permission.size)
                        permission.toList().subList(start, end).stream()
                    },
                    { getPermission(roleMatrix).size }
                ))
            }
        }
        return rootLayout
    }

    private fun getPermission(roleMatrix: RoleMatrixDTO): List<PermissionMatrixDTO> {
        return roleMatrix.permissions
            .filter { if (selectedMs == null) true else it.msName.equals(selectedMs) }
            .filter { it.privilegeKey.contains(permissionToSearch ?: "") }
    }

    fun update(update: () -> Unit) {
        if (documentAlarm.isDisposed) {
            return
        }

        documentAlarm.cancelAllRequests()
        documentAlarm.addRequest({
            getApplication().runWriteAction {
                update.invoke()
            }
        }, 50)
    }

    override fun getFile(): VirtualFile {
        return currentFile
    }

}

