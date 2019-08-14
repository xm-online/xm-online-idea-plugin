package com.icthh.xm.actions.settings

import com.icthh.xm.actions.settings.SettingService

class JSBridge(val data: SettingService, val callback: (String) -> Unit) {

    fun apply(data: Any) {
        callback(data.toString())
    }

}
