package com.icthh.xm.domain.permission.dto

import java.util.*


data class RoleMatrixDTO @JvmOverloads constructor (
    val roles: Collection<String>,
    val permissions: SortedSet<PermissionMatrixDTO> = TreeSet()
)

data class PermissionMatrixDTO(
    val msName: String,
    val privilegeKey: String
): Comparable<PermissionMatrixDTO> {

    var permissionType: PermissionType? = null
    val roles: SortedSet<String> = TreeSet()

    override fun compareTo(o: PermissionMatrixDTO): Int {
        return Comparator.comparing<PermissionMatrixDTO, String> { it.msName }
            .thenComparing<String>{ it.privilegeKey }
            .compare(this, o)
    }
}
