package com.icthh.xm


import com.intellij.openapi.project.Project

interface MainPluginService {
    fun init()
    fun dispose()
}

class MainPluginServiceImpl(val project: Project) : MainPluginService {

    override fun init() {
        // Initialization logic
    }

    override fun dispose() {
        ViewServer.embeddedServer?.stop()
    }
}



