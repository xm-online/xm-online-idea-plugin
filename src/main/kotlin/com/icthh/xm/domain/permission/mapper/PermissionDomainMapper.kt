package com.icthh.xm.domain.permission.mapper

import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.domain.permission.ReactionStrategy
import com.icthh.xm.domain.permission.dto.PermissionDTO

class PermissionDomainMapper {

    fun permissionToPermissionDto(permission: Permission): PermissionDTO {
        return PermissionDTO(permission)
    }

    fun permissionDtoToPermission(permissionDto: PermissionDTO): Permission {
        val permission = Permission(
            permissionDto.msName,
            permissionDto.roleKey,
            permissionDto.privilegeKey
        )
        permission.disabled = !permissionDto.enabled
        permission.reactionStrategy = permissionDto.reactionStrategy.toReactionStrategy()

        permission.envCondition = permissionDto.envCondition
        permission.resourceCondition = permissionDto.resourceCondition
        return permission
    }

    private fun String?.toReactionStrategy() = if (this != null) ReactionStrategy.valueOf(this.toUpperCase()) else null

}

