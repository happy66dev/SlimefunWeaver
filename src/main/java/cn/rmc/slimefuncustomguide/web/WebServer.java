package cn.rmc.slimefuncustomguide.web;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class WebServer {

    private HttpServer server;

    public void start(String bind, int port, WebApiHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", handler);
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }
}
