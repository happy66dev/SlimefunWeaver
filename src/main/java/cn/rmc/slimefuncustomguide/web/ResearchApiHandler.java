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
package cn.rmc.slimefuncustomguide.web;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class ResearchApiHandler implements HttpHandler {

    private final CustomGuidePlugin plugin;
    private final String editorHtml;

    public ResearchApiHandler(CustomGuidePlugin plugin) {
        this.plugin = plugin;
        this.editorHtml = loadFileFromJar("web/research-editor.html");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/editor.html")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { redirectToLogin(exchange); return; }
                serveHtml(exchange, editorHtml);
            } else if (path.equals("/api/researches")) {
                handleResearches(exchange, method);
            } else if (path.equals("/api/slimefun-items")) {
                handleSlimefunItems(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Research API error", e);
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
            }
        }
    }

    private String loadFileFromJar(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return "<h1>File not found: " + path + "</h1>";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<h1>Error: " + e.getMessage() + "</h1>";
        }
    }

    private void serveHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void serveJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void redirectToLogin(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().set("Location", "/?redirect=" + path.substring(1));
        exchange.sendResponseHeaders(302, -1);
    }

    private void handleResearches(HttpExchange exchange, String method) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
        if ("GET".equalsIgnoreCase(method)) {
            serveJson(exchange, buildResearchesJson());
        } else if ("PUT".equalsIgnoreCase(method)) {
            if (!WebSecurity.isWriteAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
            String body;
            try { body = WebSecurity.readBody(exchange); }
            catch (WebSecurity.BodyTooLargeException e) { exchange.sendResponseHeaders(413, -1); return; }
            if (body == null || body.isEmpty()) { exchange.sendResponseHeaders(400, -1); return; }
            saveResearchesFromJson(body);
            serveJson(exchange, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleSlimefunItems(HttpExchange exchange) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
        String query = exchange.getRequestURI().getQuery(), q = "";
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("q=")) {
                    try { q = java.net.URLDecoder.decode(param.substring(2), "UTF-8").toLowerCase(); } catch (Exception ignored) {}
                }
            }
        }
        StringBuilder sb = new StringBuilder("{\"items\":[");
        List<SlimefunItem> list;
        if (!q.isEmpty()) {
            list = new ArrayList<>();
            for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
                String name = item.getItemName(), id = item.getId();
                if ((name != null && name.toLowerCase().contains(q)) || id.toLowerCase().contains(q)) list.add(item);
            }
        } else {
            list = Slimefun.getRegistry().getEnabledSlimefunItems();
        }
        boolean first = true;
        for (SlimefunItem item : list) {
            if (!first) sb.append(','); first = false;
            sb.append('{'); appendString(sb, "id", item.getId()); sb.append(',');
            appendString(sb, "name", item.getItemName()); sb.append(',');
            Research r = item.getResearch();
            sb.append("\"research\":"); sb.append(r != null ? '"' + escapeJson(r.getKey().toString()) + '"' : "null");
            sb.append(','); ItemGroup g = item.getItemGroup();
            sb.append("\"group\":"); sb.append(g != null ? '"' + escapeJson(g.getUnlocalizedName()) + '"' : "null"); sb.append('}');
        }
        sb.append("]}"); serveJson(exchange, sb.toString());
    }

    private String buildResearchesJson() {
        Map<String, String> itemToResearch = new LinkedHashMap<>();
        for (Research r : Slimefun.getRegistry().getResearches()) {
            if (!r.isEnabled()) continue;
            List<SlimefunItem> affectedItems = r.getAffectedItems();
            if (affectedItems == null) continue;
            for (SlimefunItem item : affectedItems) itemToResearch.put(item.getId(), r.getKey().toString());
        }
        StringBuilder sb = new StringBuilder("{\"researches\":["); boolean firstR = true;
        for (Research r : Slimefun.getRegistry().getResearches()) {
            if (!firstR) sb.append(','); firstR = false;
            String fullKey = r.getKey().toString();
            String[] parts = fullKey.split(":", 2);
            String ns = parts.length > 1 ? parts[0] : "slimefun";
            String k = parts.length > 1 ? parts[1] : parts[0];
            sb.append('{'); appendString(sb, "namespace", ns); sb.append(',');
            appendString(sb, "key", k); sb.append(',');
            appendString(sb, "fullKey", fullKey); sb.append(',');
            appendString(sb, "name", r.getName(null)); sb.append(',');
            sb.append("\"levelCost\":").append(r.getLevelCost()).append(',');
            sb.append("\"moneyCost\":").append(r.getMoneyCost()).append(',');
            sb.append("\"enabled\":").append(r.isEnabled()).append(',');

            List<SlimefunItem> items = r.getAffectedItems();
            if (items == null) items = Collections.emptyList();
            sb.append("\"items\":["); boolean firstI = true;
            for (SlimefunItem it : items) {
                if (!firstI) sb.append(','); firstI = false;
                sb.append('{'); appendString(sb, "id", it.getId()); sb.append(',');
                appendString(sb, "name", it.getItemName()); sb.append('}');
            }
            sb.append("],");

            List<SlimefunItem> needItems = r.getNeedUnlockedItems();
            if (needItems == null) needItems = Collections.emptyList();
            sb.append("\"needUnlockedItems\":["); boolean firstN = true;
            for (SlimefunItem it : needItems) {
                if (!firstN) sb.append(','); firstN = false;
                sb.append('"').append(escapeJson(it.getId())).append('"');
            }
            sb.append("],\"parents\":["); boolean firstP = true;
            for (SlimefunItem it : needItems) {
                String pk = itemToResearch.get(it.getId());
                if (pk != null) { if (!firstP) sb.append(','); firstP = false; sb.append('"').append(escapeJson(pk)).append('"'); }
            }
            sb.append("],");

            sb.append("\"miningLevelNeed\":").append(r.getMiningLevelNeed()).append(',');
            sb.append("\"farmingLevelNeed\":").append(r.getFarmingLevelNeed()).append(',');
            sb.append("\"foragingLevelNeed\":").append(r.getForagingLevelNeed()).append(',');
            sb.append("\"fishingLevelNeed\":").append(r.getFishingLevelNeed()).append(',');
            sb.append("\"excavationLevelNeed\":").append(r.getExcavationLevelNeed()).append(',');
            sb.append("\"archeryLevelNeed\":").append(r.getArcheryLevelNeed()).append(',');
            sb.append("\"defenseLevelNeed\":").append(r.getDefenseLevelNeed()).append(',');
            sb.append("\"fightingLevelNeed\":").append(r.getFightingLevelNeed()).append(',');
            sb.append("\"agilityLevelNeed\":").append(r.getAgilityLevelNeed()).append(',');
            sb.append("\"enchantingLevelNeed\":").append(r.getEnchantingLevelNeed()).append(',');
            sb.append("\"alchemyLevelNeed\":").append(r.getAlchemyLevelNeed());
            sb.append('}');
        }
        sb.append("],\"itemToResearch\":{"); boolean firstM = true;
        for (Map.Entry<String, String> e : itemToResearch.entrySet()) {
            if (!firstM) sb.append(','); firstM = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(e.getValue())).append('"');
        }
        sb.append("}}"); return sb.toString();
    }

    private void saveResearchesFromJson(String json) {
        String c = json.trim();
        if (!c.startsWith("{\"researches\":[")) return;
        int start = "{\"researches\":[".length(), end = c.lastIndexOf(']');
        if (end <= start) return;
        c = c.substring(start, end);
        Config config = Slimefun.getResearchCfg();
        if (config == null) { plugin.getLogger().warning("Research config null, save aborted"); return; }
        int depth = 0, objStart = -1;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch == '{') { if (depth == 0) objStart = i; depth++; }
            else if (ch == '}') { depth--; if (depth == 0 && objStart >= 0) { applyResearchUpdate(c.substring(objStart, i + 1), config); objStart = -1; } }
            else if (ch == '"') i = skipJsonString(c, i);
        }
        config.save(); plugin.getLogger().info("Researches.yml saved via web editor");
    }

    private void applyResearchUpdate(String json, Config config) {
        String fullKey = extractJsonString(json, "fullKey"); if (fullKey == null) return;
        String[] parts = fullKey.split(":", 2);
        String ns = parts.length > 1 ? parts[0] : "slimefun", k = parts.length > 1 ? parts[1] : parts[0];
        String path = ns + "." + k;

        Integer lv = extractJsonInt(json, "levelCost"); if (lv != null) config.setValue(path + ".levelCost", lv);
        Integer mc = extractJsonInt(json, "moneyCost"); if (mc != null) config.setValue(path + ".moneyCost", mc);
        Boolean en = extractJsonBoolean(json, "enabled"); if (en != null) config.setValue(path + ".enabled", en);
        List<String> ni = extractJsonStringArray(json, "needUnlockedItems"); if (ni != null) config.setValue(path + ".need-unlocked-items", ni);

        setIfInt(config, path + ".miningLevelNeed", extractJsonInt(json, "miningLevelNeed"));
        setIfInt(config, path + ".farmingLevelNeed", extractJsonInt(json, "farmingLevelNeed"));
        setIfInt(config, path + ".foragingLevelNeed", extractJsonInt(json, "foragingLevelNeed"));
        setIfInt(config, path + ".fishingLevelNeed", extractJsonInt(json, "fishingLevelNeed"));
        setIfInt(config, path + ".excavationLevelNeed", extractJsonInt(json, "excavationLevelNeed"));
        setIfInt(config, path + ".archeryLevelNeed", extractJsonInt(json, "archeryLevelNeed"));
        setIfInt(config, path + ".defenseLevelNeed", extractJsonInt(json, "defenseLevelNeed"));
        setIfInt(config, path + ".fightingLevelNeed", extractJsonInt(json, "fightingLevelNeed"));
        setIfInt(config, path + ".agilityLevelNeed", extractJsonInt(json, "agilityLevelNeed"));
        setIfInt(config, path + ".enchantingLevelNeed", extractJsonInt(json, "enchantingLevelNeed"));
        setIfInt(config, path + ".alchemyLevelNeed", extractJsonInt(json, "alchemyLevelNeed"));
    }

    private static void setIfInt(Config config, String path, Integer v) { if (v != null) config.setValue(path, v); }

    private static String extractJsonString(String json, String key) {
        String s = "\"" + key + "\":\""; int idx = json.indexOf(s);
        if (idx < 0) { s = "\"" + key + "\":null"; idx = json.indexOf(s); return idx >= 0 ? null : null; }
        int start = idx + s.length(); StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char nx = json.charAt(i + 1);
                switch (nx) { case '"': sb.append('"'); i++; break; case '\\': sb.append('\\'); i++; break; case 'n': sb.append('\n'); i++; break; case 'r': sb.append('\r'); i++; break; case 't': sb.append('\t'); i++; break; default: sb.append(ch); break; }
            } else if (ch == '"') break; else sb.append(ch);
        }
        return sb.toString();
    }

    private static Integer extractJsonInt(String json, String key) {
        String s = "\"" + key + "\":"; int idx = json.indexOf(s); if (idx < 0) return null;
        idx += s.length(); if (idx + 4 <= json.length() && json.regionMatches(idx, "null", 0, 4)) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < json.length(); i++) { char ch = json.charAt(i); if ((ch >= '0' && ch <= '9') || ch == '-') sb.append(ch); else break; }
        if (sb.length() == 0) return null;
        try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Boolean extractJsonBoolean(String json, String key) {
        String s = "\"" + key + "\":"; int idx = json.indexOf(s); if (idx < 0) return null;
        idx += s.length(); if (json.regionMatches(idx, "true", 0, 4)) return true;
        if (json.regionMatches(idx, "false", 0, 5)) return false; return null;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        String s = "\"" + key + "\":["; int idx = json.indexOf(s); if (idx < 0) return null;
        int start = idx + s.length(); List<String> list = new ArrayList<>();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i); if (ch == ']') break;
            if (ch == '"') { StringBuilder sb = new StringBuilder(); i++;
                while (i < json.length()) {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        char nx = json.charAt(i + 1);
                        if (nx == '"') { sb.append('"'); i++; } else if (nx == '\\') { sb.append('\\'); i++; } else if (nx == 'n') { sb.append('\n'); i++; } else sb.append(json.charAt(i));
                    } else if (json.charAt(i) == '"') break; else sb.append(json.charAt(i));
                    i++;
                }
                list.add(sb.toString());
            }
        }
        return list;
    }

    private static int skipJsonString(String s, int i) {
        i++; while (i < s.length()) { if (s.charAt(i) == '\\' && i + 1 < s.length()) i += 2; else if (s.charAt(i) == '"') break; else i++; } return i;
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(escapeJson(value != null ? value : "")).append('"');
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder o = new StringBuilder();
        for (char ch : s.toCharArray()) switch (ch) { case '"': o.append("\\\""); break; case '\\': o.append("\\\\"); break; case '\n': o.append("\\n"); break; case '\r': o.append("\\r"); break; case '\t': o.append("\\t"); break; default: o.append(ch); }
        return o.toString();
    }
}
