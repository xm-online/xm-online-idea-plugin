package com.icthh.xm.domain.permission.dto

import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.utils.isTrue
import java.io.*

data class PermissionDTO(
    val msName: String,
    val roleKey: String,
    val privilegeKey: String,
    var enabled: Boolean,
    var newPermission: Boolean? = null
) : Comparable<PermissionDTO>, Serializable {

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

    fun toPrivilege() = Privilege(msName, privilegeKey, roleKey)

    override fun compareTo(o: PermissionDTO): Int {
        return Comparator.comparing<PermissionDTO, String>{ it.msName }
            .thenComparing<String>{ it.roleKey }
            .thenComparing<String>{ it.privilegeKey }
            .compare(this, o)
    }

    fun apply(other: PermissionDTO) {
        this.resourceCondition = other.resourceCondition
        this.enabled = other.enabled
        this.envCondition = other.envCondition
        this.reactionStrategy = other.reactionStrategy
    }

    fun copy(): PermissionDTO {
        val stream = ByteArrayOutputStream()
        ObjectOutputStream(stream).use {
            it.writeObject(this)
            val buffer = ByteArrayInputStream(stream.toByteArray())
            ObjectInputStream(buffer).use {
                return it.readObject() as PermissionDTO
            }
        }
    }

}

fun PermissionDTO?.same(other: PermissionDTO?): Boolean {
    if (this == null) {
        return other == null
    }

    if (other == null) return false
    if (this === other) return true

    if (msName != other.msName) return false
    if (roleKey != other.roleKey) return false
    if (privilegeKey != other.privilegeKey) return false
    if (enabled != other.enabled) return false
    if (reactionStrategy != other.reactionStrategy) return false
    if (envCondition != other.envCondition) return false
    if (resourceCondition != other.resourceCondition) return false
    if (permissionType != other.permissionType) return false

    return true
}

data class Privilege(val msName: String, val privilegeKey: String, val role: String)
