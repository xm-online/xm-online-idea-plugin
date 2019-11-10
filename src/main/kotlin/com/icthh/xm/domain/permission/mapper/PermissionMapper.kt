package com.icthh.xm.domain.permission.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.domain.permission.Permission
import com.icthh.xm.utils.log
import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.commons.lang3.StringUtils.startsWithIgnoreCase
import java.util.*

class PermissionMapper {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    fun permissionsToYml(permissions: Collection<Permission>): String? {
        try {
            val map = TreeMap<String, SortedMap<String, SortedSet<Permission>>>()
            permissions.forEach { permission ->
                val msPermissions = map.getOrPut(permission.msName, {TreeMap()})
                val permissionsByRole = msPermissions.getOrPut(permission.roleKey, {TreeSet()})
                permissionsByRole.add(permission)
            }
            return mapper.writeValueAsString(map)
        } catch (e: Exception) {
            log.error("Failed to create permissions YML file from collection, error: ${e.message}", e)
        }

        return null
    }

    fun ymlToPermissions(yml: String, msName: String? = null): Map<String, Permission> {
        val result = TreeMap<String, Permission>()
        try {
            val map = mapper.readValue<TreeMap<String, TreeMap<String, TreeSet<Permission>?>?>>(yml)
            map.filter { msName.isNullOrBlank() || startsWithIgnoreCase(it.key, msName) }
                .forEach { entry ->
                    entry.value?.forEach { roleKey, permissions ->
                        permissions?.forEach { permission ->
                            permission.msName = entry.key
                            permission.roleKey = roleKey
                            result.put(roleKey + ":" + permission.privilegeKey, permission)
                        }
                    }
                }
        } catch (e: Exception) {
            log.error("Failed to create permissions collection from YML file, error: ${e.message}", e)
        }

        return result
    }
}

