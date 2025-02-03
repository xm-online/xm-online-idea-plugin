package com.icthh.xm.xmeplugin

import com.icthh.xm.xmeplugin.utils.SocketUtils
import com.icthh.xm.xmeplugin.utils.log
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * This class is responsible for starting the server that will serve the web view dialogs like settings.
 */
object ViewServer {

    private var embeddedServer = lazy { createServer() }

    @Volatile
    var serverPort = SocketUtils.findAvailableTcpPort()
    var isDev: Boolean = false // Boolean.parseBoolean(System.getenv("IS_DEV_PLUGIN_RUN"));

    val serverUrl: String
        get() {
            if (isDev) {
                return "http://localhost:4200"
            }
            return "http://localhost:$serverPort"
        }

    @Throws(Exception::class)
    fun startServer() {
        if (!embeddedServer.isInitialized()) {
            embeddedServer.value
            log.info("\n\n\n\n\nServer url:$serverUrl\n\n\n\n\n")
        }
    }

    private fun createServer(): Server {
        val context = ServletContextHandler(ServletContextHandler.SESSIONS)

        var url = AppServlet::class.java.classLoader.getResource("static")
        if (url == null) {
            url = AppServlet::class.java.classLoader.getResource("static/")
            context.welcomeFiles = arrayOf("index.html")
        } else {
            context.welcomeFiles = arrayOf("/index.html")
        }
        val webRootUri = url?.toURI()
        context.setContextPath("/")
        context.setBaseResource(Resource.newResource(webRootUri))

        val holderPwd = ServletHolder("default", DefaultServlet::class.java)
        holderPwd.setInitParameter("dirAllowed", "true")
        context.addServlet(holderPwd, "/*")
        context.addServlet(AppServlet::class.java, "/app")

        val embeddedServer = Server(serverPort)
        embeddedServer.setHandler(context)
        embeddedServer.dump(System.err)
        embeddedServer.start()
        return embeddedServer
    }

    class AppServlet : HttpServlet() {

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            response.contentType = "text/html;charset=UTF-8"
            val out = response.outputStream
            out.print(index.replace("\${pipeId}", request.queryString))
        }

        companion object {
            private const val serialVersionUID = 1L
            private val index = readIndexHtml()

            private fun readIndexHtml(): String {
                try {
                    AppServlet::class.java.classLoader.getResourceAsStream("static/index.html").use { `is` ->
                        return IOUtils.toString(`is`, StandardCharsets.UTF_8)
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }
    }
}

