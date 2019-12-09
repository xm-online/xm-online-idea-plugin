package com.icthh.xm.domain.permission.dto

import com.icthh.xm.domain.permission.Role
import java.io.*

data class RoleDTO(

    val roleKey: String,
    var basedOn: String? = null,
    val description: String?,
    val createdDate: String?,
    val createdBy: String?,
    val updatedDate: String?,
    val updatedBy: String?
): Serializable {

    var permissions: MutableCollection<PermissionDTO>? = null
    var env: List<String>? = null

    fun copy(): RoleDTO {
        val stream = ByteArrayOutputStream()
        ObjectOutputStream(stream).use {
            it.writeObject(this)
            val buffer = ByteArrayInputStream(stream.toByteArray())
            ObjectInputStream(buffer).use {
                return it.readObject() as RoleDTO
            }
        }
    }

    constructor(role: Role) : this(
        roleKey = role.key,
        description = role.description,
        createdDate = role.createdDate,
        createdBy = role.createdBy,
        updatedDate = role.updatedDate,
        updatedBy = role.updatedBy
    )
}
