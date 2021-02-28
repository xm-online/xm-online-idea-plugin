package com.icthh.xm.domain.permission.dto

import java.io.*
import java.util.*
import kotlin.collections.HashSet


data class RoleMatrixDTO @JvmOverloads constructor (
    val roles: Collection<String>,
    val permissions: SortedSet<PermissionMatrixDTO> = TreeSet()
): Serializable {

    fun copy(): RoleMatrixDTO {
        val stream = ByteArrayOutputStream()
        ObjectOutputStream(stream).use {
            it.writeObject(this)
            val buffer = ByteArrayInputStream(stream.toByteArray())
            ObjectInputStream(buffer).use {
                return it.readObject() as RoleMatrixDTO
            }
        }
    }
}

data class PermissionMatrixDTO(
    val msName: String,
    val privilegeKey: String
): Comparable<PermissionMatrixDTO>, Serializable {

    constructor(msName: String, privilegeKey: String, permissionType: PermissionType?, roles: TreeSet<String>?): this(msName, privilegeKey) {
        this.permissionType = permissionType
        this.roles.addAll(roles ?: listOf())
    }

    var permissionType: PermissionType? = null
    val roles: SortedSet<String> = TreeSet()

    override fun compareTo(o: PermissionMatrixDTO): Int {
        return Comparator.comparing<PermissionMatrixDTO, String> { it.msName }
            .thenComparing<String>{ it.privilegeKey }
            .compare(this, o)
    }
}

