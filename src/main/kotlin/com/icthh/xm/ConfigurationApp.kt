package com.icthh.xm


import com.icthh.xm.utils.SocketUtils
import com.intellij.openapi.components.BaseComponent
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder

var serverPort: Int = 8080

class ConfigurationApp: BaseComponent {

    var embeddedServer: Server? = null

    override fun initComponent() {
        val contextHandler = ServletContextHandler(null, "/", true, false)
        contextHandler.setSessionHandler(SessionHandler())
        contextHandler.addServlet(ServletHolder(AppServlet::class.java), "/*")
        contextHandler.classLoader = AppUI::class.java.classLoader

        serverPort = 64024//SocketUtils.findAvailableTcpPort();

        val embeddedServer = Server(serverPort)
        embeddedServer.setHandler(contextHandler)
        embeddedServer.start()
        this.embeddedServer = embeddedServer
    }

    override fun disposeComponent() {
        embeddedServer?.stop()
    }

}
