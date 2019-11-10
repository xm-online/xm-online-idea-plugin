package com.icthh.xm.domain.permission.dto

import com.icthh.xm.domain.permission.Role

data class RoleDTO(

    val roleKey: String,
    var basedOn: String? = null,
    val description: String?,
    val createdDate: String?,
    val createdBy: String?,
    val updatedDate: String?,
    val updatedBy: String?
) {

    var permissions: Collection<PermissionDTO>? = null
    var env: List<String>? = null

    constructor(role: Role) : this(
        roleKey = role.key,
        description = role.description,
        createdDate = role.createdDate,
        createdBy = role.createdBy,
        updatedDate = role.updatedDate,
        updatedBy = role.updatedBy
    )
}
