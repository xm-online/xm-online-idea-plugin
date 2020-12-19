package com.icthh.xm;

import com.icthh.xm.utils.SocketUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.concurrent.atomic.AtomicBoolean;

public class ViewServer {

    public static Server embeddedServer;
    public static Integer serverPort = 54321;
    private static AtomicBoolean isInited = new AtomicBoolean(false);

    public static void startServer() throws Exception {
        if (isInited.compareAndSet(false, true)) {
            ServletContextHandler contextHandler = new ServletContextHandler(null, "/", true, false);
            contextHandler.setSessionHandler(new SessionHandler());
            contextHandler.addServlet(new ServletHolder(AppServlet.class), "/*");
            contextHandler.setClassLoader(AppUI.class.getClassLoader());

            serverPort = SocketUtils.Companion.findAvailableTcpPort();

            Server embeddedServer = new Server(serverPort);
            embeddedServer.setHandler(contextHandler);
            embeddedServer.start();
            ViewServer.embeddedServer = embeddedServer;
        }
    }
}
