package com.icthh.xm.editors.permission

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.BrowserCallback
import com.icthh.xm.domain.permission.ReactionStrategy
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.editors.VaadinEditor
import com.icthh.xm.editors.WebEditor
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import com.vaadin.data.provider.DataProvider
import com.vaadin.icons.VaadinIcons.EDIT
import com.vaadin.server.Sizeable.Unit.PIXELS
import com.vaadin.shared.ui.ValueChangeMode.BLUR
import com.vaadin.shared.ui.ValueChangeMode.TIMEOUT
import com.vaadin.ui.*
import com.vaadin.ui.Grid.SelectionMode.NONE


class WebRoleManagementEditor(val currentProject: Project, val currentFile: VirtualFile): WebEditor(currentProject, "role-management", "Role management") {

    var documentAlarm = Alarm(SWING_THREAD, this)
    var selectedMs: String? = null
    var permissionToSearch: String? = null

    fun viewComponent(): Component {

        val tenantName = currentFile.getTenantName(project)
        val tenantRoleService = TenantRoleService(tenantName, currentProject) { writeAction(it) }
        val allRoles = tenantRoleService.getAllRoles()
        var role = tenantRoleService.getRole(allRoles.firstOrNull()?.roleKey ?: "")
        val msNames = getPermission(role.permissions).map { it.msName }.toSet()
        val reactions = ReactionStrategy.values().toList().map { it.name }

        lateinit var grid: Grid<PermissionDTO>
        val rootLayout = VerticalLayout().apply {
            setSizeFull()

            horizontalLayout {
                comboBox<String> {
                    placeholder = "Role"
                    isEmptySelectionAllowed = false
                    setItems(allRoles.map { it.roleKey })
                    setSelectedItem(allRoles.firstOrNull()?.roleKey ?: "")
                    addValueChangeListener {
                        role = tenantRoleService.getRole(it.value)
                        grid.refresh()
                    }
                }
                comboBox<String> {
                    setItems(msNames)
                    addValueChangeListener {
                        selectedMs = it.value
                        grid.refresh()
                    }
                }
                textField {
                    placeholder = "Privilege"
                    setWidth(300f, PIXELS)
                    onEnterPressed {
                        permissionToSearch = it.value
                        grid.refresh()
                    }
                    addValueChangeListener {
                        permissionToSearch = it.value
                        grid.refresh()
                    }
                    valueChangeMode = TIMEOUT
                }
            }
            grid = grid {
                expandRatio = 1f;
                setColumnReorderingAllowed(true);
                setSelectionMode(NONE)
                setSizeFull()

                val privilegeKey = addColumn{ it.privilegeKey }
                privilegeKey.setCaption("Privilege key")
                privilegeKey.isSortable = true

                val msName = addColumn{ it.msName }
                msName.setCaption("MS name")
                msName.isHidable = true

                val enabled = addComponentColumn { permission ->
                    CheckBox().apply {
                        value = permission.enabled
                        addValueChangeListener {
                            permission.enabled = it.value
                            getApplication().executeOnPooledThread {
                                tenantRoleService.updateRole(role.copy())
                            }
                        }
                    }
                }
                enabled.isSortable = true
                enabled.caption = "Permit"

                val onForbid = addComponentColumn { permission ->
                    ComboBox<String>().apply {
                        setItems(reactions)
                        permission.reactionStrategy?.apply { setSelectedItem(this) }
                        addValueChangeListener {
                            permission.reactionStrategy = it.value
                            getApplication().executeOnPooledThread {
                                tenantRoleService.updateRole(role.copy())
                            }
                        }
                    }
                }
                onForbid.caption = "On Forbid"
                onForbid.isHidable = true

                val resourceCondition = addComponentColumn { permission ->
                    val resources = permission.resources
                    HorizontalLayout().apply {
                        val popupView = PopupView(permission.resourceCondition ?: EDIT.html,
                            VerticalLayout().apply {
                                label {
                                    html("""
                                        1. use SpEL functionality<br>
                                        2. use # before variable<br>
                                        3. #subject.role, #subject.userKey, #subject.login are available<br>
                                        ${resources?.joinToString(separator = ", ", prefix = "Available variables: ")}            
                                    """.trimIndent())
                                }
                                textArea {
                                    rows = 5
                                    setWidth(600F, PIXELS)
                                    value = permission.resourceCondition ?: ""
                                    valueChangeMode = BLUR
                                    addValueChangeListener {
                                        permission.resourceCondition = it.value.ifEmpty { null }
                                        getApplication().executeOnPooledThread {
                                            tenantRoleService.updateRole(role.copy())
                                        }
                                        grid.refresh()
                                    }
                                }
                            }
                        )
                        popupView.isHideOnMouseOut = false
                        addComponent(popupView)
                    }
                }

                resourceCondition.caption = "Resource Condition"
                resourceCondition.isHidable = true

                val envCondition = addComponentColumn { permission ->
                    HorizontalLayout().apply {
                        val popupView = PopupView(permission.envCondition ?: EDIT.html,
                            VerticalLayout().apply {
                                label {
                                    html("""
                                        1. use SpEL functionality<br>
                                        2. use # before variable<br>
                                        3. #subject.role, #subject.userKey, #subject.login are available<br>
                                        4. #env['ipAddress'] is available
                                    """.trimIndent())
                                }
                                textArea {
                                    rows = 5
                                    setWidth(600F, PIXELS)
                                    value = permission.envCondition ?: ""
                                    valueChangeMode = BLUR
                                    addValueChangeListener {
                                        permission.envCondition = it.value.ifEmpty { null }
                                        getApplication().executeOnPooledThread {
                                            tenantRoleService.updateRole(role.copy())
                                        }
                                        grid.refresh()
                                    }
                                }
                            }
                        )
                        popupView.isHideOnMouseOut = false
                        addComponent(popupView)
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

    private fun getPermission(rolePermissions: Collection<PermissionDTO>?): List<PermissionDTO> {
        return (rolePermissions ?: emptyList())
            .filter { if (selectedMs == null) true else it.msName.equals(selectedMs) }
            .filter { it.privilegeKey.contains(permissionToSearch ?: "") }
    }

    fun writeAction(update: () -> Unit) {
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

    override fun callbacks(browser: JBCefBrowser): List<BrowserCallback> {
        return listOf(
            BrowserCallback("componentReady") {body, pipe ->


            }
        )
    }

}

