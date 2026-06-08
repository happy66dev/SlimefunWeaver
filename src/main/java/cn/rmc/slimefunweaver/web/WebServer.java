// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy (k666kkk666k@163.com)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package cn.rmc.slimefunweaver.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.Map;

public class WebServer {

    private HttpServer server;

    public void start(String bind, int port, WebApiHandler handler,
                      boolean categoriesEditor, boolean recipesEditor, boolean researchesEditor) throws Exception {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", handler);
        if (researchesEditor) {
            ResearchApiHandler researchHandler = new ResearchApiHandler(handler.getPlugin());
            server.createContext("/editor.html", researchHandler);
            server.createContext("/api/researches", researchHandler);
            server.createContext("/api/slimefun-items", researchHandler);
        }
        if (recipesEditor) {
            RecipeApiHandler recipeHandler = new RecipeApiHandler(handler.getPlugin());
            server.createContext("/recipes.html", recipeHandler);
            server.createContext("/api/recipes", recipeHandler);
            server.createContext("/api/recipes/materials", recipeHandler);
            server.createContext("/api/recipe-types", recipeHandler);
        }
        server.setExecutor(null);
        server.start();
    }

    public void start(String bind, int port, Map<String, HttpHandler> contexts) throws Exception {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        for (Map.Entry<String, HttpHandler> entry : contexts.entrySet()) {
            server.createContext(entry.getKey(), entry.getValue());
        }
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
