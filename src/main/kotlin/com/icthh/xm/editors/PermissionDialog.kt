package com.icthh.xm.editors

import com.github.mvysny.karibudsl.v8.*
import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.actions.permission.GitContentProvider
import com.icthh.xm.domain.permission.ReactionStrategy
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.domain.permission.dto.RoleDTO
import com.icthh.xm.domain.permission.dto.same
import com.icthh.xm.service.getTenantName
import com.icthh.xm.service.permission.TenantRoleService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.vaadin.server.Sizeable.Unit.PIXELS
import com.vaadin.shared.ui.MarginInfo
import com.vaadin.ui.*
import com.vaadin.ui.Alignment.MIDDLE_CENTER
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

    val reactions = ReactionStrategy.values().toList().map { it.name }


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

        var diffPermissions = allPrivileges.filter { !thisPrivileges[it].same(otherPrivileges[it]) }.map {
            val permission = it
            val roleDTO = roles.first { it.roleKey == permission.role }
            ComparePermissionDiff(it, thisPrivileges[it], otherPrivileges[it], roleDTO)
        }

        diffPermissions = diffPermissions.sortedBy { it.privilege.role + it.privilege.privilegeKey }

        val permissionCards = ArrayList<PermissionCard>()
        var cardPosition = 0
        diffPermissions.forEach {
            val height = it.getHeight()
            permissionCards.add(PermissionCard(cardPosition, height, it))
            cardPosition += height
        }

        return Panel().apply {
            val permissionRows = ArrayList<HorizontalLayout>()
            val panel = this
            setSizeFull()
            id = "mainpanel"

            verticalLayout {
                isSpacing = false
                margin = MarginInfo(false, false, false, false)
                val prefix = verticalLayout {
                    setHeight(1f, PIXELS)
                    setWidth(1f, PIXELS)
                    isSpacing = false
                    margin = MarginInfo(false, false, false, false)
                }
                val cards = verticalLayout {
                    setSizeUndefined()
                    setWidth("100%")
                }
                val suffix = verticalLayout {
                    setHeight(1f, PIXELS)
                    setWidth(1f, PIXELS)
                    isSpacing = false
                    margin = MarginInfo(false, false, false, false)
                }
                cards.updateCards(panel, permissionCards, prefix, suffix, permissionRows)
                UI.getCurrent().isResizeLazy = true
                UI.getCurrent().page.addBrowserWindowResizeListener {
                    cards.updateCards(panel, permissionCards, prefix, suffix, permissionRows)
                }
                onScroll{
                    UI.getCurrent().access {
                        cards.updateCards(panel, permissionCards, prefix, suffix, permissionRows)
                    }
                }

            }
        }
    }

    private fun VerticalLayout.updateCards(
        panel: Panel,
        permissionCards: MutableList<PermissionCard>,
        prefix: VerticalLayout,
        suffix: VerticalLayout,
        permissionRows: MutableList<HorizontalLayout>
    ) {
        val startPosition = -2000
        val endPosition = panel.height + 1500
        val position = panel.scrollTop.toFloat()
        val beforeCards = permissionCards.filter { it.position < position + startPosition }
        val afterCards = permissionCards.filter { it.position > position + endPosition }
        val viewCards = permissionCards.filter {
            it.position >= position + startPosition && it.position <= position + endPosition
        }
        val viewPositions = viewCards.map{it.position}
        val min = permissionRows.map { it.getCardData()?.position }.filterNotNull().min() ?: Integer.MIN_VALUE
        var max = permissionRows.map { it.getCardData()?.position }.filterNotNull().max() ?: Integer.MAX_VALUE
        if (permissionRows.isEmpty()) {
            max = 0;
        }
        viewCards.forEach {
            val row = createRow(it.permissionDiff, it)
            permissionRows.add(row)
            if (it.position < min) {
                this.addComponent(row, 0)
            }
            if (it.position > max) {
                this.addComponent(row)
            }
        }
        prefix.setHeight(beforeCards.sumBy { it.height }.toFloat(), PIXELS)
        suffix.setHeight(afterCards.sumBy { it.height }.toFloat(), PIXELS)
        permissionRows.forEach {
            val card = it.getCardData()
            if (!viewPositions.contains(card?.position)) {
                this.removeComponent(it)
            }
        }
        permissionRows.removeIf {
            !viewPositions.contains(it.getCardData()?.position)
        }
    }

    private fun HorizontalLayout.getCardData() = this.data as PermissionCard?

    private fun VerticalLayout.createRow(
        permission: ComparePermissionDiff,
        card: PermissionCard
    ): HorizontalLayout {
        val currentBranch = permission.currentBranch
        val otherBranch = permission.otherBranch
        return HorizontalLayout().apply {
            setSizeFull()
            data = card
            val left = permissionCart(
                otherBranch,
                permission.isBranchPermissionNeedResourceSpel(),
                permission.isBranchPermissionNeedEnvSpel(),
                readOnly = true
            )
            val apply = verticalLayout {
                val button = button(">>") {

                }
                setComponentAlignment(button, MIDDLE_CENTER)
                isSpacing = false
                margin = MarginInfo(true, false, false, false)
            }
            val right = permissionCart(
                currentBranch,
                permission.isCurrentPermissionNeedResourceSpel(),
                permission.isCurrentPermissionNeedEnvSpel()
            )
            right.setHeight(permission.getHeight().toFloat(), PIXELS)
            left.setHeight(permission.getHeight().toFloat(), PIXELS)
            setExpandRatio(left, 0.45f)
            setExpandRatio(apply, 0.05f)
            setExpandRatio(right, 0.45f)
            isSpacing = false
        }
    }

    private fun onScroll(onscroll: () -> Unit) {
        JavaScript.getCurrent().addFunction("com.icthh.xm.onscroll") {
            onscroll.invoke()
        }
        JavaScript.getCurrent().execute("""
            setTimeout(function() {
                console.log("scroll init");                
                document.querySelector('#mainpanel .v-scrollable').addEventListener("scroll", function(e) {
                    console.log("scroll");
                    clearTimeout(window.timer);
                    window.timer = setTimeout(function() {
                        com.icthh.xm.onscroll();
                    }, 70);
                });
            }, 3000);
        """.trimIndent())
    }

    private fun HorizontalLayout.permissionCart(permission: PermissionDTO?,
                                                needResourceSpel: Boolean,
                                                needEnvSpel: Boolean,
                                                readOnly: Boolean = false) = panel {
        verticalLayout {
            if (permission == null) {
                label("Permission not exists")
                return@verticalLayout
            }
            horizontalLayout {
                label(permission.roleKey)
                label(":")
                label(permission.privilegeKey)
                label("(${permission.msName})")
            }
            horizontalLayout {
                comboBox<String> {
                    setItems(reactions)
                    isReadOnly = readOnly
                    permission.reactionStrategy?.apply { setSelectedItem(this) }
                    addValueChangeListener {
                        permission.reactionStrategy = it.value
                        // TODO
                    }
                    placeholder = "On Forbid"
                }
                val isEnabled = checkBox(
                    checked = permission.enabled,
                    caption = "Enabled"
                ) {
                    isReadOnly = readOnly
                }
                setComponentAlignment(isEnabled, MIDDLE_CENTER)
            }
            if (needResourceSpel) {
                horizontalLayout {
                    textArea {
                        caption = "Resource condition"
                        value = permission.resourceCondition ?: ""
                        isReadOnly = readOnly
                        setSizeFull()
                    }
                    setSizeFull()
                }
            }
            if (needEnvSpel) {
                horizontalLayout {
                    textArea {
                        caption = "Environment condition"
                        value = permission.envCondition ?: ""
                        isReadOnly = readOnly
                        setSizeFull()
                    }
                    setSizeFull()
                }
            }
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
    return Dimension(Math.max(screenSize.width * 8 / 10, 1200), screenSize.height * 8 / 10)
}

data class ComparePermissionDiff(
    val privilege: PrivilegeItemKey,
    val currentBranch: PermissionDTO?,
    val otherBranch: PermissionDTO?,
    val origin: RoleDTO
) {
    fun isBranchPermissionNeedResourceSpel() = !otherBranch?.resourceCondition.isNullOrBlank()
    fun isBranchPermissionNeedEnvSpel() = !otherBranch?.envCondition.isNullOrBlank()

    fun isCurrentPermissionNeedResourceSpel() =
        isBranchPermissionNeedResourceSpel() || !currentBranch?.resourceCondition.isNullOrBlank()
    fun isCurrentPermissionNeedEnvSpel() =
        isBranchPermissionNeedEnvSpel() || !currentBranch?.envCondition.isNullOrBlank()

    fun getBranchHeight(): Int {
        val envSpel = isBranchPermissionNeedEnvSpel()
        val resSpel = isBranchPermissionNeedResourceSpel()
        return getHeight(envSpel, resSpel)
    }

    fun getCurrentHeight(): Int {
        val envSpel = isCurrentPermissionNeedEnvSpel()
        val resSpel = isCurrentPermissionNeedResourceSpel()
        return getHeight(envSpel, resSpel)
    }

    private fun getHeight(envSpel: Boolean, resSpel: Boolean): Int {
        if (envSpel && resSpel) {
            return 425
        }
        if (envSpel || resSpel) {
            return 265
        }
        return 100
    }

    fun getHeight() = Math.max(getBranchHeight(), getCurrentHeight())
}

data class PrivilegeItemKey(val privilegeKey: String, val msName: String, val role: String)

fun PermissionDTO.toKey() = PrivilegeItemKey(privilegeKey, msName, roleKey)

data class PermissionCard(
    val position: Int,
    val height: Int,
    val permissionDiff: ComparePermissionDiff
)
