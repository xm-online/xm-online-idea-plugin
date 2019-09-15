package com.icthh.xm.actions.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.*
import kotlin.collections.ArrayList

@State(name = "xm^Online.Settings")
data class SettingService(
    var envs: MutableList<EnvironmentSettings> = ArrayList(),
    var selectedEnv: String? = null
) : PersistentStateComponent<SettingService> {

    override fun getState() = this

    override fun loadState(state: SettingService) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

data class EnvironmentSettings(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var xmUrl: String = "",
    var xmSuperAdminLogin: String = "",
    var xmSuperAdminPassword: String = ""
) {
    override fun toString() = name
}


