package com.icthh.xm.domain.permission.dto

import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.utils.isTrue

data class PermissionDTO(
    val msName: String,
    val roleKey: String,
    val privilegeKey: String,
    val enabled: Boolean
) : Comparable<PermissionDTO> {


    var reactionStrategy: String? = null
    var envCondition: String? = null
    var resourceCondition: String? = null
    var resources: Set<String>? = null
    var permissionType: PermissionType? = null

    constructor(permission: Permission) : this(
        msName = permission.msName,
        roleKey = permission.roleKey,
        privilegeKey = permission.privilegeKey,
        enabled = permission.disabled.isTrue().not()
    ) {
        reactionStrategy = permission.reactionStrategy?.name
        envCondition = permission.envCondition
        resourceCondition = permission.resourceCondition
    }

    override fun compareTo(o: PermissionDTO): Int {
        return Comparator.comparing<PermissionDTO, String>{ it.msName }
            .thenComparing<String>{ it.roleKey }
            .thenComparing<String>{ it.privilegeKey }
            .compare(this, o)
    }
}
