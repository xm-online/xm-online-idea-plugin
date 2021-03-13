package com.icthh.xm.domain.permission.mapper

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.domain.permission.Privilege
import com.icthh.xm.utils.log
import java.util.*

class PrivilegeMapper {

        private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun privilegesToYml(privileges: Collection<Privilege>): String? {
            try {
                val map = TreeMap<String, SortedSet<Privilege>>()
                privileges.forEach { privilege ->
                    val msPrivileges = map.getOrPut(privilege.msName, {TreeSet()})
                    msPrivileges.add(privilege)
                }
                return mapper.writeValueAsString(map)
            } catch (e: Exception) {
                log.error("Failed to create privileges YML file from collection, error: ${e.message}", e)
                throw e
            }
        }

        fun privilegesMapToYml(privileges: Map<String, Collection<Privilege>>): String? {
            try {
                return mapper.writeValueAsString(privileges)
            } catch (e: Exception) {
                log.error("Failed to create privileges YML file from map, error: ${e.message}", e)
                throw e
            }
        }

        fun ymlToPrivileges(yml: String): MutableMap<String, MutableSet<Privilege>> {
            try {
                val map = mapper.readValue<MutableMap<String, MutableSet<Privilege>>>(yml)
                map.forEach { (msName, privileges) -> privileges.forEach { privilege -> privilege.msName = msName } }
                return map
            } catch (e: Exception) {
                log.error("Failed to create privileges collection from YML file, error: ${e.message}", e)
                throw e
            }
        }
}
