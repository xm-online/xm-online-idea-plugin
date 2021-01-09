package com.icthh.xm;

import com.icthh.xm.utils.SocketUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ViewServer {

    private static final Logger LOGGER = java.util.logging.Logger.getLogger(ViewServer.class.getSimpleName());

    public static Server embeddedServer;
    private static Integer serverPort = 54321;
    private static AtomicBoolean isInited = new AtomicBoolean(false);
    public static boolean isDev = false;// Boolean.parseBoolean(System.getenv("IS_DEV_PLUGIN_RUN"));

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
            context.addServlet(AppServlet.class, "/app");

            serverPort = SocketUtils.Companion.findAvailableTcpPort();

            Server embeddedServer = new Server(serverPort);
            embeddedServer.setHandler(context);
            embeddedServer.start();
            embeddedServer.dump(System.err);
            ViewServer.embeddedServer = embeddedServer;

            LOGGER.info("\n\n\nServer url:" + getServerUrl() + "\n\n\n");
        }
    }

    public static class AppServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private static final String index = readIndexHtml();

        public AppServlet() {
        }

        private static String readIndexHtml() {
            try {
                return IOUtils.toString(AppServlet.class.getClassLoader().getResourceAsStream("/static/index.html"), UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            var out = response.getOutputStream();
            out.print(index.replace("${pipeId}", request.getQueryString()));
        }
    }

}

