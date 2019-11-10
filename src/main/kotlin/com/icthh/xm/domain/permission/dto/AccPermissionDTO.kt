package com.icthh.xm.domain.permission.dto

import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.utils.isTrue


data class AccPermissionDTO(
    val msName: String,
    val roleKey: String,
    val privilegeKey: String,
    val isEnabled: Boolean
) {

    constructor(permission: Permission) : this(
        msName = permission.msName,
        roleKey = permission.roleKey,
        privilegeKey = permission.privilegeKey,
        isEnabled = permission.disabled.isTrue().not()
    )

}
