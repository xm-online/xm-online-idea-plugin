package com.icthh.xm.domain.permission.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.domain.permission.Role
import com.icthh.xm.utils.log
import java.util.*
import java.util.Collections.emptyMap

class RoleMapper {

    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    fun rolesToYml(roles: Collection<Role>): String? {
        try {
            val map = TreeMap<String, Role>()
            roles.forEach { role -> map[role.key] = role }
            return mapper.writeValueAsString(map)
        } catch (e: Exception) {
            log.error("Failed to create roles YML file from collection, error: ${e.message}", e)
        }

        return null
    }

    fun ymlToRoles(yml: String): Map<String, Role> {
        try {
            val map = mapper.readValue<TreeMap<String, Role>>(yml)
            map.forEach{ roleKey, role -> role.key = roleKey }
            return map
        } catch (e: Exception) {
            log.error("Failed to create roles collection from YML file, error: ${e.message}", e)
        }

        return emptyMap()
    }
}
