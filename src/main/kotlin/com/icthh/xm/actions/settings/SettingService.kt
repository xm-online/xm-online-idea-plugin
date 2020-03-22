package com.icthh.xm.actions.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

@State(name = "xm^Online.Settings")
class SettingService: PersistentStateComponent<SettingService> {

    var envs: MutableList<EnvironmentSettings> = ArrayList()
    var selectedEnv: String? = null

    override fun getState() = this

    override fun loadState(state: SettingService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun selected() = envs.find { it.id == selectedEnv }

    fun select(settings: EnvironmentSettings?) {
        selectedEnv = settings?.id
    }
}

enum class UpdateMode(val isGitMode: Boolean) {
    INCREMENTAL(false), FROM_START(false), GIT_LOCAL_CHANGES(true), GIT_BRANCH_DIFFERENCE(true)
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
    var startTrackChangesOnEdit: Boolean = true

    var trackChanges: Boolean = false
    var lastChangedFiles: MutableSet<String> = HashSet()
    var editedFiles: MutableMap<String, FileState> = HashMap()
    var ignoredFiles: MutableSet<String> = HashSet()
    var atStartFilesState: MutableMap<String, FileState> = HashMap()
    var version: String? = null

    var lastTimeTryToNotifyAboutDifference: Long = 0

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

class FileState {
    constructor()
    constructor(sha256: String) {
        this.sha256 = sha256
    }

    var sha256: String = ""
    var isNotified: Boolean = false
}
