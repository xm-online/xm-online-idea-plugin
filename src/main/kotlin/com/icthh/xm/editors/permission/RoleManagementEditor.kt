package com.icthh.xm.editors.permission

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.domain.permission.ReactionStrategy
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.domain.permission.dto.RoleDTO
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
import com.vaadin.shared.ui.ValueChangeMode.BLUR
import com.vaadin.ui.*
import com.vaadin.ui.Grid.SelectionMode.NONE


class RoleManagementEditor(val currentProject: Project, val currentFile: VirtualFile): VaadinEditor(currentProject, "role-management", "Role management") {

    var documentAlarm = Alarm(SWING_THREAD, this)
    var selectedMs: String? = null
    var permissionToSearch: String? = null

    override fun viewComponent(): Component {

        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject)
        val role = tenantRoleService.getRole(tenantRoleService.getAllRoles().first().roleKey)
        val msNames = getPermission(role.permissions).map { it.msName }.toSet()

        lateinit var grid: Grid<PermissionDTO>
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

                val enabled = addComponentColumn { permission ->
                    CheckBox().apply {
                        value = permission.enabled
                        addValueChangeListener {
                            permission.enabled = it.value
                            updateValue(role, tenantRoleService)
                        }
                    }
                }
                enabled.isSortable = true
                enabled.caption = "Permit"

                val onForbid = addComponentColumn { permission ->
                    ComboBox<String>().apply {
                        setItems(ReactionStrategy.values().toList().map { it.name })
                        permission.reactionStrategy?.apply { setSelectedItem(this) }
                        addValueChangeListener {
                            permission.reactionStrategy = it.value
                            updateValue(role, tenantRoleService)
                        }
                    }
                }
                onForbid.caption = "On Forbid"
                onForbid.isHidable = true

                val resourceCondition = addComponentColumn { permission ->
                    TextArea().apply {
                        value = permission.resourceCondition ?: ""
                        valueChangeMode = BLUR
                        onEnterPressed {
                            permission.resourceCondition = it.value.ifEmpty { null }
                            updateValue(role, tenantRoleService)
                        }
                        addValueChangeListener {
                            permission.resourceCondition = it.value.ifEmpty { null }
                            updateValue(role, tenantRoleService)
                        }
                    }
                }
                resourceCondition.caption = "Resource Condition"
                resourceCondition.isHidable = true

                val envCondition = addComponentColumn { permission ->
                    TextArea().apply {
                        value = permission.envCondition ?: ""
                        valueChangeMode = BLUR
                        onEnterPressed {
                            permission.envCondition = it.value.ifEmpty { null }
                            updateValue(role, tenantRoleService)
                        }
                        addValueChangeListener {
                            permission.envCondition = it.value.ifEmpty { null }
                            updateValue(role, tenantRoleService)
                        }
                    }
                }
                envCondition.caption = "Environment Condition"
                envCondition.isHidable = true

                setDataProvider(DataProvider.fromCallbacks(
                    { query ->
                        val permission = getPermission(role.permissions)
                        val start = query.getOffset()
                        val end = Math.min(query.getLimit() + start, permission.size)
                        permission.toList().subList(start, end).stream()
                    },
                    { getPermission(role.permissions).size }
                ))
            }
        }
        return rootLayout
    }

    private fun updateValue(
        role: RoleDTO,
        tenantRoleService: TenantRoleService
    ) {
        update {
            val mapper = jacksonObjectMapper()
            val copy = mapper.readValue<RoleDTO>(mapper.writeValueAsBytes(role))
            tenantRoleService.updateRole(copy)
        }
    }

    private fun getPermission(rolePermissions: Collection<PermissionDTO>?): List<PermissionDTO> {
        return (rolePermissions ?: emptyList())
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

