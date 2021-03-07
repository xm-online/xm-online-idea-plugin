package com.icthh.xm


import com.icthh.xm.utils.SocketUtils
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.util.registry.Registry
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder

class ConfigurationApp: BaseComponent {

    override fun initComponent() {

    }

    override fun disposeComponent() {
        ViewServer.embeddedServer?.stop()
    }

}
