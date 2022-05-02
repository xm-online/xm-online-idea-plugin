package com.icthh.xm


import com.intellij.openapi.components.BaseComponent

class ConfigurationApp: BaseComponent {

    override fun initComponent() {

    }

    override fun disposeComponent() {
        ViewServer.embeddedServer?.stop()
    }

}
