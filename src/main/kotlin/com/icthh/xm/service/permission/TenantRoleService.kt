package com.icthh.xm.service.permission

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.domain.permission.Privilege
import com.icthh.xm.domain.permission.Role
import com.icthh.xm.domain.permission.dto.PermissionDTO
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO
import com.icthh.xm.domain.permission.dto.PermissionType.SYSTEM
import com.icthh.xm.domain.permission.dto.PermissionType.TENANT
import com.icthh.xm.domain.permission.dto.RoleDTO
import com.icthh.xm.domain.permission.dto.RoleMatrixDTO
import com.icthh.xm.domain.permission.mapper.PermissionDomainMapper
import com.icthh.xm.domain.permission.mapper.PermissionMapper
import com.icthh.xm.domain.permission.mapper.PrivilegeMapper
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.log
import org.apache.commons.lang3.StringUtils.isBlank
import java.time.Instant
import java.util.*
import kotlin.streams.toList

class TenantRoleService {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun getRoles(): MutableMap<String, Role> {
        return getConfig<TreeMap<String, Role>>(ROLES_PATH) ?: TreeMap()
    }

    fun getPermissions(): SortedMap<String, SortedMap<String, SortedSet<Permission>>> {
        return getConfig<SortedMap<String, SortedMap<String, SortedSet<Permission>>>>(PERMISSIONS_PATH) ?: TreeMap()
    }

    fun getPrivileges(): Map<String, Set<Privilege>> {
        return getConfigContent(PRIVILEGES_PATH).map {
            PrivilegeMapper().ymlToPrivileges(it)
        }.orElse(TreeMap())
    }

    fun getCustomPrivileges(): Map<String, Set<Privilege>> {
        val tenant = "tenant name"
        return getConfigContent(CUSTOM_PRIVILEGES_PATH.replace("{tenantName}", tenant)).map {
            PrivilegeMapper().ymlToPrivileges(it)
        }.orElse(TreeMap())
    }


    fun getAllRoles() = getRoles().entries.stream()
        .peek { it.value.key = it.key }
        .map{ it.value }
        .map{ RoleDTO(it) }.toList().toSet()

    fun getEnvironments() = getConfig<List<String>>(ENV_PATH)

    // map key = MS_NAME:PRIVILEGE_KEY, value = PermissionMatrixDTO
    // create permissions matrix dto with role permissions
    // enrich role permissions with missing privileges
    fun getRoleMatrix(): RoleMatrixDTO {
        val roleMatrix = RoleMatrixDTO(getRoles().keys)
        val matrixPermissions = HashMap<String, PermissionMatrixDTO>()
        getPermissions().forEach { (msName, rolePermissions) ->
            rolePermissions.forEach { (roleKey, permissions) ->
                permissions.forEach { permission ->
                    var permissionMatrix: PermissionMatrixDTO? =
                        matrixPermissions[msName + ":" + permission.privilegeKey]
                    if (permissionMatrix == null) {
                        permissionMatrix = PermissionMatrixDTO(msName, permission.privilegeKey)
                        matrixPermissions[msName + ":" + permission.privilegeKey] = permissionMatrix
                    }
                    if (permission.disabled.isTrue().not()) {
                        permissionMatrix.roles.add(roleKey)
                    }
                }
            }
        }

        getPrivileges().values.forEach{processPrivilege(it, roleMatrix, matrixPermissions)}
        roleMatrix.permissions.forEach { it.permissionType = SYSTEM }
        val customPrivileges = getCustomPrivileges()
        customPrivileges.values.forEach{processPrivilege(it, roleMatrix, matrixPermissions)}
        val customPrivilegeKeys = customPrivileges.values.stream()
            .flatMap<Privilege>{ it.stream() }
            .map<String> { it.key }
            .toList().toSet()
        roleMatrix.permissions.stream()
            .filter { customPrivilegeKeys.contains(it.privilegeKey) }
            .forEach {
                if (it.permissionType == SYSTEM) {
                    log.error("Custom privilege {} try to override system privilege, and ignored")
                } else {
                    it.permissionType = TENANT
                }
            }

        return roleMatrix
    }

    fun processPrivilege(privileges: Set<Privilege>, roleMatrix: RoleMatrixDTO,
                         matrixPermissions: HashMap<String, PermissionMatrixDTO>) {
        val elements = privileges.map {
            val key = it.msName + ":" + it.key
            matrixPermissions.getOrDefault(key, PermissionMatrixDTO(it.msName, it.key))
        }.toList()
        roleMatrix.permissions.addAll(elements)
    }

    fun addRole(roleDto: RoleDTO) {

        val roles = getRoles()

        if (null != roles[roleDto.roleKey]) {
            log.warn("Role already exists")
            return
        }

        val role = Role(roleDto.roleKey)
        role.description = roleDto.description
        role.createdBy = "TODO"
        role.createdDate = Instant.now().toString()
        role.updatedBy = "TBD"
        role.updatedDate = roleDto.updatedDate
        roles[roleDto.roleKey] = role

        updateRoles(mapper.writeValueAsString(roles))

        val permissions = getPermissions()
        if (roleDto.basedOn.isNullOrBlank()) {
            enrichExistingPermissions(permissions, roleDto.roleKey)
        } else {
            enrichExistingPermissions(permissions, roleDto.roleKey, roleDto.basedOn)
        }

        updatePermissions(mapper.writeValueAsString(permissions))
    }

    fun updateRole(roleDto: RoleDTO) {
        val roles = getRoles()
        val role = roles[roleDto.roleKey] ?: throw RuntimeException("Role doesn't exist")

        role.description = roleDto.description
        role.updatedBy = "rbd"
        role.updatedDate = Instant.now().toString()
        roles[roleDto.roleKey] = role
        updateRoles(mapper.writeValueAsString(roles))

        val newPermissions = roleDto.permissions
        if (newPermissions.isNullOrEmpty()) {
            return
        }

        // permission updating
        val existingPermissions = getPermissions()
        enrichExistingPermissions(existingPermissions, newPermissions)

        updatePermissions(mapper.writeValueAsString(existingPermissions))
    }

    fun getRolePermissions(roleKey: String): List<PermissionDTO> {
        return getConfigContent(PERMISSIONS_PATH)
            .map { PermissionMapper().ymlToPermissions(it) }
            .map { permission ->
                permission.entries.stream()
                    .map{ it.value }
                    .filter { roleKey == it.roleKey }
                    .map{ PermissionDTO(it) }.toList()
            }
            .orElse(emptyList())
    }

    private fun updateRoles(rolesYml: String) {}

    private fun updatePermissions(permissionsYml: String) {}

    fun getRole(roleKey: String): Optional<RoleDTO> {
        val role = getRoles()[roleKey] ?: return Optional.empty()
        role.key = roleKey
        val roleDto = RoleDTO(role)
        roleDto.permissions = TreeSet()

        // map key = MS_NAME:PRIVILEGE_KEY, value = PermissionDTO
        val permissions = TreeMap<String, PermissionDTO>()

        // create permissions dto with role permissions
        getPermissions().forEach { (msName, rolePermissions) ->
            rolePermissions
                .filter { roleKey.equals(it.key, ignoreCase = true) }
                .forEach { entry ->
                    entry.value.forEach { permission ->
                        permission.msName = msName
                        permission.roleKey = roleKey
                        val key = msName + ":" + permission.privilegeKey
                        permissions[key] = PermissionDTO(permission)
                    }
                }
        }

        getPrivileges().forEach{ privilegesProcessor(it.key, it.value, permissions, roleKey, roleDto) }
        roleDto.permissions?.forEach { it -> it.permissionType = SYSTEM }
        val customPrivileges = getCustomPrivileges()
        customPrivileges.forEach{ privilegesProcessor(it.key, it.value, permissions, roleKey, roleDto) }
        val customPrivilegeKeys = customPrivileges.values.stream()
            .flatMap{ it.stream() }
            .map{ it.key }.toList().toSet()
        roleDto.permissions?.stream()?.filter {
            customPrivilegeKeys.contains(it.privilegeKey)
        }?.forEach {
            if (it.permissionType == SYSTEM) {
                log.error("Custom privilege ${it} try to override system privilege, and ignored");
            } else {
                it.permissionType = TENANT
            }
        }

        roleDto.env = getEnvironments()

        return Optional.of(roleDto)
    }

    fun privilegesProcessor(msName: String, privileges: Set<Privilege>, permissions: TreeMap<String, PermissionDTO>,
                            roleKey: String, roleDto: RoleDTO) {
        privileges.forEach { privilege ->
            var permission: PermissionDTO? = permissions[msName + ":" + privilege.key]
            if (permission == null) {
                permission = PermissionDTO(msName, roleKey, privilege.key, false)
            }
            permission.resources = privilege.resources
            roleDto.permissions?.add(permission)
        }
    }

    fun deleteRole(roleKey: String) {
        val roles = getRoles()
        roles.remove(roleKey)
        updateRoles(mapper.writeValueAsString(roles))

        val permissions = getPermissions()
        for (perm in permissions.values) {
            perm.remove(roleKey)
        }
        updatePermissions(mapper.writeValueAsString(permissions))

    }

    fun updateRoleMatrix(roleMatrix: RoleMatrixDTO) {
        // create map key: MS_NAME:PRIVILEGE_KEY, value: PermissionMatrixDTO for easy search
        val newPermissions = HashMap<String, PermissionMatrixDTO>()
        for (permission in roleMatrix.permissions) {
            newPermissions[permission.msName + ":" + permission.privilegeKey] = permission
        }

        val allPermissions = getPermissions()
        allPermissions.forEach { (msName, rolePermissions) ->
            rolePermissions.entries.stream()
                // do not update hidden roles
                .filter { roleWithPermissions -> roleMatrix.roles.contains(roleWithPermissions.key) }
                // roleWithPermissions -> key: ROLE_KEY, value: set of role permissions
                .forEach { roleWithPermissions ->
                    roleWithPermissions.value.forEach { permission ->
                        val key = msName + ":" + permission.privilegeKey
                        val permissionMatrixDTO = newPermissions[key]
                        if (permissionMatrixDTO != null) {
                            /*
                             * disable permissions for current ROLE_KEY if it
                             * is not present in roleMatrix.permissions[].roles[] list
                             */
                            val roles = permissionMatrixDTO.roles
                            if (roles.contains(roleWithPermissions.key)) {
                                permission.disabled = false
                                roles.remove(roleWithPermissions.key)
                            } else {
                                permission.disabled = true
                            }
                        }
                    }
                }
        }

        // processing permissions for new role
        roleMatrix.permissions.stream().filter { permissionMatrixDTO -> !permissionMatrixDTO.roles.isEmpty() }
            .forEach { permissionMatrixDTO ->
                val msPermissions = allPermissions.getOrPut(permissionMatrixDTO.msName, {TreeMap()})
                permissionMatrixDTO.roles.forEach { role ->
                    val rolePermissions = msPermissions.getOrPut(role, {TreeSet()})
                    val permission = Permission(
                        permissionMatrixDTO.msName,
                        role,
                        permissionMatrixDTO.privilegeKey
                    )
                    permission.disabled = false
                    rolePermissions.add(permission)
                }
            }
        updatePermissions(mapper.writeValueAsString(allPermissions))
    }

    private fun enrichExistingPermissions(
        existingPermissions: SortedMap<String, SortedMap<String, SortedSet<Permission>>>,
        role: String,
        basedOn: String?
    ) {
        for (perm in existingPermissions.values) {
            perm.put(role, perm.getOrDefault(basedOn, TreeSet()))
        }
    }

    private fun enrichExistingPermissions(
        existingPermissions: Map<String, MutableMap<String, SortedSet<Permission>>>,
        role: String
    ) {
        for (perm in existingPermissions.values) {
            perm.put(role, TreeSet())
        }
    }

    private fun enrichExistingPermissions(
        existingPermissions: SortedMap<String, SortedMap<String, SortedSet<Permission>>>,
        newPermissions: Collection<PermissionDTO>
    ) {
        newPermissions.forEach { permissionDto ->
            val msPermissions = existingPermissions.getOrPut(permissionDto.msName, {TreeMap()})
            val rolePermissions = msPermissions.getOrPut(permissionDto.roleKey, {TreeSet()})
            val permission = PermissionDomainMapper().permissionDtoToPermission(permissionDto)
            // needed explicitly delete old permission
            rolePermissions.remove(permission)
            rolePermissions.add(permission)
        }
    }

    private inline fun <reified T> getConfig(configPath: String): T? {
        val config = getConfigContent(configPath).orElse(EMPTY_YAML)
        return mapper.readValue<T>(config)
    }

    private fun getConfigContent(configPath: String): Optional<String> {
        val tenant = "tenant name" // TODO
        var config: String? = null
        config = "config content" // TODO
        if (isBlank(config) || EMPTY_YAML == config) {
            config = null
        }
        return Optional.empty()
    }

    companion object {

        private val EMPTY_YAML = "---"
        private val CUSTOM_PRIVILEGES_PATH = "/config/tenants/{tenantName}/custom-privileges.yml"
        private val PRIVILEGES_PATH = "/config/tenants/privileges.yml"
        private val PERMISSIONS_PATH = "/config/tenants/{tenantName}/permissions.yml"
        private val ROLES_PATH = "/config/tenants/{tenantName}/roles.yml"
        private val ENV_PATH = "/config/tenants/environments.yml"
    }
}
