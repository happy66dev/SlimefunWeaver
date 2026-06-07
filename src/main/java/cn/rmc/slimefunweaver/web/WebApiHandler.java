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

import cn.rmc.slimefunweaver.SlimefunWeaver;
import cn.rmc.slimefunweaver.model.CustomCategory;
import cn.rmc.slimefunweaver.util.VanillaMaterialLocalization;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public class WebApiHandler implements HttpHandler {

    private final SlimefunWeaver plugin;
    private final String indexHtml;

    public WebApiHandler(SlimefunWeaver plugin) {
        this.plugin = plugin;
        this.indexHtml = loadFileFromJar("web/index.html");
    }

    public SlimefunWeaver getPlugin() {
        return plugin;
    }

    private String loadFileFromJar(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return "<h1>" + path + " not found in jar</h1>";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<h1>Error: " + e.getMessage() + "</h1>";
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/login")) {
                handleLogin(exchange, method);
            } else if (path.equals("/") || path.equals("/index.html")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { serveContent(exchange, loginHtml(), "text/html"); return; }
                serveContent(exchange, indexHtml, "text/html");
            } else if (path.equals("/style.css")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                serveContent(exchange, loadFileFromJar("web/style.css"), "text/css");
            } else if (path.equals("/editor.js")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                serveContent(exchange, loadFileFromJar("web/editor.js"), "application/javascript");
            } else if (path.equals("/api/categories")) {
                handleCategories(exchange, method);
            } else if (path.equals("/api/materials")) {
                handleMaterials(exchange);
            } else if (path.equals("/api/item-groups")) {
                handleItemGroups(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Web API error", e);
            try { exchange.sendResponseHeaders(500, -1); } catch (IOException ignored) {}
        }
    }

    private void handleLogin(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String body;
        try {
            body = WebSecurity.readBody(exchange);
        } catch (WebSecurity.BodyTooLargeException e) {
            exchange.sendResponseHeaders(413, -1);
            return;
        }
        if (!WebSecurity.isLoginValid(plugin, body)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        String token = plugin.getConfig().getString("web-editor.token", "");
        WebSecurity.setAuthCookie(exchange, token);
        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private String loginHtml() {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>登录</title></head><body style=\"background:#0d1117;color:#c9d1d9;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh\"><form onsubmit=\"var t=document.getElementById('t').value;var rd=new URLSearchParams(location.search).get('redirect')||'/';fetch('/api/login',{method:'POST',body:'token='+encodeURIComponent(t)}).then(function(r){if(r.ok)location.href=rd;else alert('Token错误')});return false\" style=\"background:#161b22;padding:24px;border:1px solid #30363d;border-radius:8px\"><div style=\"margin-bottom:12px\">Web 编辑器 Token</div><input id=\"t\" type=\"password\" autofocus style=\"padding:8px;background:#0d1117;color:#c9d1d9;border:1px solid #30363d\"><button style=\"margin-left:8px;padding:8px\">登录</button></form></body></html>";
    }

    private void serveContent(HttpExchange exchange, String content, String contentType) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void handleCategories(HttpExchange exchange, String method) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            List<CustomCategory> cats = plugin.getRootCategories();
            String json = JsonUtil.categoriesToJson(cats);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else if ("PUT".equalsIgnoreCase(method)) {
            if (!WebSecurity.isWriteAllowed(plugin, exchange)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            String body;
            try {
                body = WebSecurity.readBody(exchange);
            } catch (WebSecurity.BodyTooLargeException e) {
                exchange.sendResponseHeaders(413, -1);
                return;
            }
            if (body == null || body.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            saveCategoriesFromJson(body);
            plugin.reloadCategories();
            byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void saveCategoriesFromJson(String json) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("categories");

        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject()) return;
        JsonObject rootObj = parsed.getAsJsonObject();
        JsonArray categoriesArr = (rootObj.has("categories") && rootObj.get("categories").isJsonArray())
                ? rootObj.getAsJsonArray("categories") : null;
        if (categoriesArr == null) return;

        for (JsonElement elem : categoriesArr) {
            if (elem.isJsonObject()) {
                parseCategoryFromJson(elem.getAsJsonObject(), root);
            }
        }

        File tempFile = new File(plugin.getDataFolder(), "categories.yml.tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            writer.write(yaml.saveToString());
            writer.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save categories.yml from web editor", e);
            return;
        }
        File finalFile = new File(plugin.getDataFolder(), "categories.yml");
        try {
            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.move(tempFile.toPath(), finalFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                plugin.getLogger().log(Level.WARNING, "Failed to replace categories.yml", e2);
            }
        }
    }

    private void parseCategoryFromJson(JsonObject obj, ConfigurationSection parent) {
        String key = (obj.has("key") && !obj.get("key").isJsonNull()) ? obj.get("key").getAsString() : null;
        if (key == null || key.isEmpty()) return;

        ConfigurationSection section = parent.createSection(key);

        if (obj.has("display") && !obj.get("display").isJsonNull())
            section.set("display", obj.get("display").getAsString());

        if (obj.has("page") && !obj.get("page").isJsonNull())
            section.set("page", obj.get("page").getAsInt());

        if (obj.has("slot") && !obj.get("slot").isJsonNull())
            section.set("slot", obj.get("slot").getAsInt());

        if (obj.has("glow") && !obj.get("glow").isJsonNull() && obj.get("glow").getAsBoolean())
            section.set("glow", true);

        if (obj.has("icon") && obj.get("icon").isJsonObject()) {
            JsonObject iconObj = obj.getAsJsonObject("icon");
            ConfigurationSection iconSection = section.createSection("icon");
            if (iconObj.has("type"))
                iconSection.set("type", iconObj.get("type").getAsString());
            if (iconObj.has("id"))
                iconSection.set("id", iconObj.get("id").getAsString());
        }

        if (obj.has("lore") && obj.get("lore").isJsonArray()) {
            JsonArray loreArr = obj.getAsJsonArray("lore");
            List<String> lore = new ArrayList<>();
            for (JsonElement e : loreArr) {
                if (e.isJsonPrimitive()) lore.add(e.getAsString());
            }
            if (!lore.isEmpty()) section.set("lore", lore);
        }

        if (obj.has("items") && obj.get("items").isJsonArray()) {
            JsonArray itemsArr = obj.getAsJsonArray("items");
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (JsonElement itemElem : itemsArr) {
                if (!itemElem.isJsonObject()) continue;
                JsonObject itemObj = itemElem.getAsJsonObject();
                String type = itemObj.has("type") ? itemObj.get("type").getAsString() : "ITEM";
                if ("REFERENCE".equals(type)) {
                    Map<String, Object> ph = new LinkedHashMap<>();
                    Map<String, Object> refData = new LinkedHashMap<>();
                    if (itemObj.has("ref")) refData.put("ref", itemObj.get("ref").getAsString());
                    if (itemObj.has("mode")) refData.put("mode", itemObj.get("mode").getAsString());
                    if (itemObj.has("display") && !itemObj.get("display").isJsonNull())
                        refData.put("display", itemObj.get("display").getAsString());
                    if (itemObj.has("glow") && !itemObj.get("glow").isJsonNull() && itemObj.get("glow").getAsBoolean())
                        refData.put("glow", true);
                    if (itemObj.has("icon") && itemObj.get("icon").isJsonObject()) {
                        JsonObject rIcon = itemObj.getAsJsonObject("icon");
                        Map<String, Object> iconMap = new LinkedHashMap<>();
                        if (rIcon.has("type")) iconMap.put("type", rIcon.get("type").getAsString());
                        if (rIcon.has("id")) iconMap.put("id", rIcon.get("id").getAsString());
                        refData.put("icon", iconMap);
                    }
                    if (itemObj.has("lore") && itemObj.get("lore").isJsonArray()) {
                        List<String> rLore = new ArrayList<>();
                        for (JsonElement e : itemObj.getAsJsonArray("lore")) if (e.isJsonPrimitive()) rLore.add(e.getAsString());
                        if (!rLore.isEmpty()) refData.put("lore", rLore);
                    }
                    if (itemObj.has("page")) refData.put("page", itemObj.get("page").getAsInt());
                    if (itemObj.has("slot")) refData.put("slot", itemObj.get("slot").getAsInt());
                    ph.put("placeholder", refData);
                    itemList.add(ph);
                } else if ("PLACEHOLDER".equals(type)) {
                    Map<String, Object> ph = new LinkedHashMap<>();
                    Map<String, Object> phIcon = new LinkedHashMap<>();
                    if (itemObj.has("icon") && itemObj.get("icon").isJsonObject()) {
                        JsonObject pIcon = itemObj.getAsJsonObject("icon");
                        if (pIcon.has("type")) phIcon.put("type", pIcon.get("type").getAsString());
                        if (pIcon.has("id")) phIcon.put("id", pIcon.get("id").getAsString());
                    }
                    Map<String, Object> placeholder = new LinkedHashMap<>();
                    placeholder.put("icon", phIcon);
                    if (itemObj.has("display") && !itemObj.get("display").isJsonNull())
                        placeholder.put("display", itemObj.get("display").getAsString());
                    if (itemObj.has("glow") && !itemObj.get("glow").isJsonNull() && itemObj.get("glow").getAsBoolean())
                        placeholder.put("glow", true);
                    if (itemObj.has("lore") && itemObj.get("lore").isJsonArray()) {
                        List<String> pLore = new ArrayList<>();
                        for (JsonElement e : itemObj.getAsJsonArray("lore")) {
                            if (e.isJsonPrimitive()) pLore.add(e.getAsString());
                        }
                        if (!pLore.isEmpty()) placeholder.put("lore", pLore);
                    }
                    if (itemObj.has("page")) placeholder.put("page", itemObj.get("page").getAsInt());
                    if (itemObj.has("slot")) placeholder.put("slot", itemObj.get("slot").getAsInt());
                    ph.put("placeholder", placeholder);
                    itemList.add(ph);
                } else {
                    Map<String, Object> itemMap = new LinkedHashMap<>();
                    itemMap.put("id", itemObj.has("id") ? itemObj.get("id").getAsString() : "UNKNOWN");
                    if (itemObj.has("page")) itemMap.put("page", itemObj.get("page").getAsInt());
                    if (itemObj.has("slot")) itemMap.put("slot", itemObj.get("slot").getAsInt());
                    itemList.add(itemMap);
                }
            }
            if (!itemList.isEmpty()) section.set("items", itemList);
        }

        if (obj.has("children") && obj.get("children").isJsonArray()) {
            JsonArray childrenArr = obj.getAsJsonArray("children");
            for (JsonElement childElem : childrenArr) {
                if (childElem.isJsonObject()) {
                    parseCategoryFromJson(childElem.getAsJsonObject(), section);
                }
            }
        }
    }

    private void handleMaterials(HttpExchange exchange) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String q = "";
        String typeFilter = "";
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("q=")) {
                    q = URLDecoder.decode(param.substring(2), StandardCharsets.UTF_8).toLowerCase();
                } else if (param.startsWith("type=")) {
                    typeFilter = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8).toUpperCase();
                }
            }
        }

        if (q.isEmpty()) {
            byte[] bytes = "{\"results\":[],\"hint\":\"enter a search query\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
            return;
        }

        StringBuilder sb = new StringBuilder("{\"results\":[");
        boolean first = true;
        boolean truncated = false;

        if (typeFilter.isEmpty() || typeFilter.equals("VANILLA")) {
            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().contains(q)) {
                    if (!first) sb.append(',');
                    sb.append("{\"type\":\"VANILLA\",\"id\":\"").append(mat.name());
                    sb.append("\",\"display\":\"").append(JsonUtil.escape(VanillaMaterialLocalization.getItemName(mat))).append("\"}");
                    first = false;
                    if (sb.length() > 5000) {
                        truncated = true;
                        break;
                    }
                }
            }
        }

        if (!truncated && (typeFilter.isEmpty() || typeFilter.equals("SLIMEFUN"))) {
            for (SlimefunItem sfItem : Slimefun.getRegistry().getEnabledSlimefunItems()) {
                String name = sfItem.getItemName();
                String id = sfItem.getId();
                if ((name != null && name.toLowerCase().contains(q)) || id.toLowerCase().contains(q)) {
                    if (!first) sb.append(',');
                    sb.append("{\"type\":\"SLIMEFUN\",\"id\":\"").append(JsonUtil.escape(id));
                    sb.append("\",\"display\":\"").append(JsonUtil.escape(name != null ? name : id));
                    String groupName = sfItem.getItemGroup() != null ? sfItem.getItemGroup().getUnlocalizedName() : "unknown";
                    sb.append("\",\"group\":\"").append(JsonUtil.escape(groupName)).append("\"}");
                    first = false;
                    if (sb.length() > 10000) {
                        truncated = true;
                        break;
                    }
                }
            }
        }

        if (truncated) {
            sb.append("],\"truncated\":true}");
        } else {
            sb.append("]}");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void handleItemGroups(HttpExchange exchange) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        StringBuilder sb = new StringBuilder("{\"groups\":[");
        boolean first = true;
        for (io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (!first) sb.append(',');
            sb.append("{\"key\":\"").append(JsonUtil.escape(group.getUnlocalizedName()));
            sb.append("\",\"display\":\"").append(JsonUtil.escape(
                    group.getUnlocalizedName()
            )).append("\"}");
            first = false;
        }
        sb.append("]}");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

}
