package com.icthh.xm.editors.permission

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.editors.VaadinEditor
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.vaadin.data.provider.DataProvider
import com.vaadin.data.provider.DataProviderListener
import com.vaadin.data.provider.Query
import com.vaadin.server.Sizeable.Unit.PERCENTAGE
import com.vaadin.server.Sizeable.Unit.PIXELS
import com.vaadin.shared.Registration
import com.vaadin.ui.*
import java.util.*
import java.util.stream.Stream

class RoleMatrixEditor(val currentProject: Project, val currentFile: VirtualFile): VaadinEditor(currentProject, "role-matrix", "Role matrix") {
    override fun viewComponent(): Component {

        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject)
        var roleMatrix = tenantRoleService.getRoleMatrix()

        lateinit var grid: Grid<PermissionMatrixDTO>
        val rootLayout = VerticalLayout().apply {
            setSizeFull()

            horizontalLayout {
                comboBox<String> {  }
                textField {
                    setWidth(300f, PIXELS)
                }
            }
            grid = grid {
                expandRatio = 1f;
                setColumnReorderingAllowed(true);
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
                                getApplication().invokeLater ({
                                    getApplication().runWriteAction {
                                        tenantRoleService.updateRoleMatrix(roleMatrix)
                                        roleMatrix = tenantRoleService.getRoleMatrix()
                                        grid.refresh()
                                    }
                                }, ModalityState.stateForComponent(getComponent()))
                            }
                        }
                    }
                    column.isSortable = false
                    column.caption = role
                    column.isHidable = true
                }
                setDataProvider( DataProvider.fromCallbacks(
                    { query ->
                        val offset = query.getOffset();
                        val limit = query.getLimit();
                        roleMatrix.permissions.toList().subList(offset, offset + limit).stream()
                    },
                    {query -> roleMatrix.permissions.size}
                ))
            }
        }
        return rootLayout
    }
}

