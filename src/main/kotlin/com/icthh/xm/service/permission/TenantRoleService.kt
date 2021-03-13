package com.icthh.xm.service.permission

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.domain.permission.Privilege
import com.icthh.xm.domain.permission.Role
import com.icthh.xm.domain.permission.dto.*
import com.icthh.xm.domain.permission.dto.PermissionType.SYSTEM
import com.icthh.xm.domain.permission.dto.PermissionType.TENANT
import com.icthh.xm.domain.permission.mapper.PermissionDomainMapper
import com.icthh.xm.domain.permission.mapper.PermissionMapper
import com.icthh.xm.domain.permission.mapper.PrivilegeMapper
import com.icthh.xm.extensions.entityspec.xmEntitySpecService
import com.icthh.xm.service.configPathToRealPath
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.log
import com.icthh.xm.utils.logger
import com.icthh.xm.utils.readTextAndClose
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.apache.commons.lang.time.StopWatch
import org.apache.commons.lang3.StringUtils.isBlank
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.collections.HashSet
import kotlin.streams.toList

open class TenantRoleService(val tenant: String, val project: Project, val writeAction: (() -> Unit) -> Unit = { it.invoke() }) {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun getRoles(): MutableMap<String, Role> {
        var configPath = ROLES_PATH.replace("{tenantName}", tenant)
        configPath = project.configPathToRealPath(configPath)
        return getConfig<TreeMap<String, Role>>(configPath) ?: TreeMap()
    }

    fun getPermissions(): SortedMap<String, SortedMap<String, SortedSet<Permission>>> {
        var configPath = PERMISSIONS_PATH.replace("{tenantName}", tenant)
        configPath = project.configPathToRealPath(configPath)
        return getConfig<SortedMap<String, SortedMap<String, SortedSet<Permission>>>>(configPath) ?: TreeMap()
    }

    fun getPrivileges(): Map<String, Set<Privilege>> {
        var configPath = PRIVILEGES_PATH
        configPath = project.configPathToRealPath(configPath)
        return getConfigContent(configPath).map {
            PrivilegeMapper().ymlToPrivileges(it)
        }.orElse(TreeMap())
    }

    fun getCustomPrivileges(): Map<String, Set<Privilege>> {
        var configPath = CUSTOM_PRIVILEGES_PATH.replace("{tenantName}", tenant)
        configPath = project.configPathToRealPath(configPath)
        val customPrivileges: MutableMap<String, MutableSet<Privilege>> = getConfigContent(configPath).map {
            PrivilegeMapper().ymlToPrivileges(it)
        }.orElse(TreeMap())

        var tenantConfig = TENANT_CONFIG.replace("{tenantName}", tenant)
        tenantConfig = project.configPathToRealPath(tenantConfig)
        val functionPermissionEnabled: Boolean = getConfigContent(tenantConfig).map {
            mapper.readTree(it)?.get("entity-functions")?.get("dynamicPermissionCheckEnabled")?.booleanValue() ?: false
        }.orElse(false)

        if (functionPermissionEnabled) {
            val xmEntitySpec = project.xmEntitySpecService.getByTenant(tenant)
            val functionKeys = xmEntitySpec.functionKeys
            val functionKeysWithEntityId = xmEntitySpec.functionKeysWithEntityId ?: listOf()
            customPrivileges.putIfAbsent("entity-functions", TreeSet())
            val privileges = customPrivileges.getOrDefault("entity-functions", TreeSet())
            functionKeys.forEach {
                if (functionKeysWithEntityId.contains(it)) {
                    privileges.add(Privilege("entity-functions", "XMENTITY.FUNCTION.${it}"))
                } else {
                    privileges.add(Privilege("entity-functions", "FUNCTION.CALL.${it}"))
                }
            }
        }

        return customPrivileges
    }

    fun getEnvironments(): List<String>? {
        var configPath = ENV_PATH
        configPath = project.configPathToRealPath(configPath)
        return getConfig<List<String>>(configPath)
    }

    fun getAllRoles() = getRoles().entries.stream()
        .peek { it.value.key = it.key }
        .map{ it.value }
        .map{ RoleDTO(it) }.toList().toSet()

    // map key = MS_NAME:PRIVILEGE_KEY, value = PermissionMatrixDTO
    // create permissions matrix dto with role permissions
    // enrich role permissions with missing privileges
    fun getRoleMatrix(): RoleMatrixDTO {
        val roleMatrix = RoleMatrixDTO(HashSet(getRoles().keys))
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
        role.updatedBy = "tbd"
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

    private fun updateRoles(rolesYml: String) {
        // TODO
    }

    private fun updatePermissions(permissionsYml: String) {
        var configPath = PERMISSIONS_PATH.replace("{tenantName}", tenant)
        configPath = project.configPathToRealPath(configPath)
        saveConfigContent(permissionsYml, configPath)
    }

    fun getRole(roleKey: String): RoleDTO {
        val role = getRoles()[roleKey] ?: return RoleDTO(Role())
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

        return roleDto
    }

    fun privilegesProcessor(msName: String, privileges: Set<Privilege>, permissions: TreeMap<String, PermissionDTO>,
                            roleKey: String, roleDto: RoleDTO) {
        privileges.forEach { privilege ->
            var permission: PermissionDTO? = permissions[msName + ":" + privilege.key]
            if (permission == null) {
                permission = PermissionDTO(msName, roleKey, privilege.key, false, true)
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
        newPermissions.filter { it.enabled || !it.newPermission.isTrue() }.forEach { permissionDto ->
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

    open protected fun getConfigContent(configPath: String): Optional<String> {
        val virtualFile = VfsUtil.findFile(File(configPath).toPath(), true)
        if (virtualFile?.exists() != true) {
            return Optional.empty()
        }
        val config = virtualFile.inputStream.readTextAndClose()
        if (isBlank(config) || EMPTY_YAML == config) {
            return Optional.empty()
        }
        return Optional.of(config)
    }

    private fun saveConfigContent(content: String, configPath: String) {
        val virtualFile = VfsUtil.findFile(File(configPath).toPath(), true)
        val byteArray = content.toByteArray()
        writeAction.invoke {
            val time = StopWatch()
            time.start()
            if (virtualFile?.exists() == true) {
                virtualFile.setBinaryContent(byteArray)
            }
            logger.info("Time save file ${time.time}")
        }
    }

    companion object {

        private val EMPTY_YAML = "---"
        private val TENANT_CONFIG = "/config/tenants/{tenantName}/tenant-config.yml"
        private val CUSTOM_PRIVILEGES_PATH = "/config/tenants/{tenantName}/custom-privileges.yml"
        private val PRIVILEGES_PATH = "/config/tenants/privileges.yml"
        private val PERMISSIONS_PATH = "/config/tenants/{tenantName}/permissions.yml"
        private val ROLES_PATH = "/config/tenants/{tenantName}/roles.yml"
        private val ENV_PATH = "/config/tenants/environments.yml"
        private val ENTITY_SPEC_PATH = "config/tenants/{tenantName}/entity/xmentityspec.yml"
    }
}
