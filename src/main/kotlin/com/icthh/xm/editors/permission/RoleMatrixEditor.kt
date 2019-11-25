package com.icthh.xm.editors.permission

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.editors.VaadinEditor
import com.icthh.xm.service.getConfigRelatedPath
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.vaadin.server.Sizeable.Unit.PIXELS
import com.vaadin.ui.*
import com.vaadin.ui.Alignment.MIDDLE_CENTER

class RoleMatrixEditor(val currentProject: Project, val currentFile: VirtualFile): VaadinEditor(currentProject, "role-matrix", "Role matrix") {
    override fun viewComponent(): Component {

        val tenantName = currentFile.getTenantName(project)
        val roleMatrix = TenantRoleService(tenantName, currentProject).getRoleMatrix()

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
                setSizeFull()

                val privilegeKey = addColumn{ it.privilegeKey }
                privilegeKey.setCaption("Privilege key")

                val msName = addColumn{ it.msName }
                msName.setCaption("MS name")

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
                            }
                        }
                    }
                    column.isSortable = false
                    column.caption = role
                }
                setItems(roleMatrix.permissions)
            }
        }
        return rootLayout
    }
}
