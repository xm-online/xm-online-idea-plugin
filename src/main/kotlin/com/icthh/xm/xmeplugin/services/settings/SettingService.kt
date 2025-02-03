package com.icthh.xm.xmeplugin.services.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.utils.Feature
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "xm^Online.Settings")
class SettingService: PersistentStateComponent<SettingService> {

    var envs: MutableList<EnvironmentSettings> = ArrayList()
    @Volatile
    var selectedEnv: String? = null

    override fun getState() = this

    override fun loadState(state: SettingService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun selected(): EnvironmentSettings? {
        if (NULL_ENV.id == selectedEnv) {
            return NULL_ENV
        }
        return envs.find { it.id == selectedEnv }
    }

    fun select(settings: EnvironmentSettings?) {
        selectedEnv = settings?.id
    }
}

enum class UpdateMode {
    GIT_LOCAL_CHANGES, GIT_BRANCH_DIFFERENCE
}


public val NULL_ENV = EnvironmentSettings().let {
    it.name = "No env"
    it
}

class EnvironmentSettings {

    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var xmUrl: String = ""
    get() = field.trim('/')

    var xmSuperAdminLogin: String = ""
    var xmSuperAdminPassword: String = ""
    var clientId: String = "webapp"
    var clientPassword: String = "webapp"
    var updateMode: UpdateMode = UpdateMode.GIT_LOCAL_CHANGES
    var branchName: String = "HEAD"

    var lastChangedFiles: MutableSet<String> = HashSet()
    var lastChangedState: MutableMap<String, String> = HashMap()
    var ignoredFiles: MutableSet<String> = HashSet()
    var version: String? = null

    var basePath: String? = null

    var selectedTenants: MutableSet<String> = HashSet()
    var selectedFeatures: MutableSet<Feature> = HashSet()

    override fun toString() = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EnvironmentSettings

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun copy(): EnvironmentSettings = jacksonObjectMapper().readValue(jacksonObjectMapper().writeValueAsString(this))

}
