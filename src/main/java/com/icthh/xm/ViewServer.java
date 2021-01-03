package com.icthh.xm;

import com.icthh.xm.utils.SocketUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class ViewServer {

    private static final Logger LOGGER = java.util.logging.Logger.getLogger(ViewServer.class.getSimpleName());

    public static Server embeddedServer;
    private static Integer serverPort = 54321;
    private static AtomicBoolean isInited = new AtomicBoolean(false);
    public static boolean isDev = true;

    public static String getServerUrl() {
        if (isDev) {
            return "http://localhost:4200";
        }
        return "http://localhost:" + serverPort;
    }

    public static void startServer() throws Exception {
        if (isInited.compareAndSet(false, true)) {

            URL url = ViewServer.class.getClassLoader().getResource("/static/");
            URI webRootUri = url.toURI();
            ServletContextHandler context = new ServletContextHandler(
                    ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            context.setBaseResource(Resource.newResource(webRootUri));
            context.setWelcomeFiles(new String[] { "index.html" });

            ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
            holderPwd.setInitParameter("dirAllowed", "true");
            context.addServlet(holderPwd, "/*");

            serverPort = SocketUtils.Companion.findAvailableTcpPort();

            Server embeddedServer = new Server(serverPort);
            embeddedServer.setHandler(context);
            embeddedServer.start();
            embeddedServer.dump(System.err);
            ViewServer.embeddedServer = embeddedServer;

            LOGGER.info("\n\n\nServer url:" + getServerUrl() + "\n\n\n");
        }
    }
}
