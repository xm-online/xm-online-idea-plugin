package com.icthh.xm

import com.intellij.openapi.components.BaseComponent
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

class ConfigurationApp: BaseComponent {

    override fun initComponent() {
        val server = HttpServer.create(InetSocketAddress(8000), 0)
        server.createContext("/test") { t ->
            val response = "<html><head></head><body><div>This is the response</div></body></html>"
            t.sendResponseHeaders(200, 0)
            val os = t.responseBody
            os.write(response.toByteArray())
            os.close()
        }
        server.executor = null
        server.start()
    }

    override fun disposeComponent() {

    }

}
