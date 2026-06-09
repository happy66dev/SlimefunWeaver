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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class ResearchApiHandler implements HttpHandler {

    private final SlimefunWeaver plugin;
    private final String editorHtml;

    public ResearchApiHandler(SlimefunWeaver plugin) {
        this.plugin = plugin;
        this.editorHtml = WebApiHandler.loadFileFromJar(plugin, "web/research-editor.html");
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
            if (!saveResearchesFromJson(body)) { exchange.sendResponseHeaders(500, -1); return; }
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
                    try { q = java.net.URLDecoder.decode(param.substring(2), "UTF-8").toLowerCase(); } catch (Exception e) { plugin.getLogger().log(Level.WARNING, "URL decode failed for query: " + param, e); }
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
            String fullKey = safeResearchKey(r);
            if (fullKey == null) continue;
            List<SlimefunItem> affectedItems = safeAffectedItems(r);
            if (affectedItems == null) continue;
            for (SlimefunItem item : affectedItems) {
                String itemId = safeItemId(item);
                if (itemId != null) itemToResearch.put(itemId, fullKey);
            }
        }
        StringBuilder sb = new StringBuilder("{\"researches\":["); boolean firstR = true;
        for (Research r : Slimefun.getRegistry().getResearches()) {
            String fullKey = safeResearchKey(r);
            if (fullKey == null) continue;
            if (!firstR) sb.append(','); firstR = false;
            String[] parts = fullKey.split(":", 2);
            String ns = parts.length > 1 ? parts[0] : "slimefun";
            String k = parts.length > 1 ? parts[1] : parts[0];
            sb.append('{'); appendString(sb, "namespace", ns); sb.append(',');
            appendString(sb, "key", k); sb.append(',');
            appendString(sb, "fullKey", fullKey); sb.append(',');
            appendString(sb, "name", safeResearchName(r, k)); sb.append(',');
            sb.append("\"levelCost\":").append(safeInt(() -> r.getLevelCost(), 0)).append(',');
            sb.append("\"moneyCost\":").append(safeDouble(() -> r.getMoneyCost(), 0)).append(',');
            sb.append("\"enabled\":").append(safeResearchEnabled(fullKey, true)).append(',');

            List<SlimefunItem> items = safeAffectedItems(r);
            if (items == null) items = Collections.emptyList();
            sb.append("\"items\":["); boolean firstI = true;
            for (SlimefunItem it : items) {
                String itemId = safeItemId(it);
                if (itemId == null) continue;
                if (!firstI) sb.append(','); firstI = false;
                sb.append('{'); appendString(sb, "id", itemId); sb.append(',');
                appendString(sb, "name", safeItemName(it, itemId)); sb.append('}');
            }
            sb.append("],");

            List<SlimefunItem> needItems = safeNeedItems(r);
            if (needItems == null) needItems = Collections.emptyList();
            sb.append("\"needUnlockedItems\":["); boolean firstN = true;
            for (SlimefunItem it : needItems) {
                String itemId = safeItemId(it);
                if (itemId == null) continue;
                if (!firstN) sb.append(','); firstN = false;
                sb.append('"').append(escapeJson(itemId)).append('"');
            }
            sb.append("],\"parents\":["); boolean firstP = true;
            for (SlimefunItem it : needItems) {
                String itemId = safeItemId(it);
                if (itemId == null) continue;
                String pk = itemToResearch.get(itemId);
                if (pk != null) { if (!firstP) sb.append(','); firstP = false; sb.append('"').append(escapeJson(pk)).append('"'); }
            }
            sb.append("],");

            sb.append("\"miningLevelNeed\":").append(safeInt(() -> r.getMiningLevelNeed(), 0)).append(',');
            sb.append("\"farmingLevelNeed\":").append(safeInt(() -> r.getFarmingLevelNeed(), 0)).append(',');
            sb.append("\"foragingLevelNeed\":").append(safeInt(() -> r.getForagingLevelNeed(), 0)).append(',');
            sb.append("\"fishingLevelNeed\":").append(safeInt(() -> r.getFishingLevelNeed(), 0)).append(',');
            sb.append("\"excavationLevelNeed\":").append(safeInt(() -> r.getExcavationLevelNeed(), 0)).append(',');
            sb.append("\"archeryLevelNeed\":").append(safeInt(() -> r.getArcheryLevelNeed(), 0)).append(',');
            sb.append("\"defenseLevelNeed\":").append(safeInt(() -> r.getDefenseLevelNeed(), 0)).append(',');
            sb.append("\"fightingLevelNeed\":").append(safeInt(() -> r.getFightingLevelNeed(), 0)).append(',');
            sb.append("\"agilityLevelNeed\":").append(safeInt(() -> r.getAgilityLevelNeed(), 0)).append(',');
            sb.append("\"enchantingLevelNeed\":").append(safeInt(() -> r.getEnchantingLevelNeed(), 0)).append(',');
            sb.append("\"alchemyLevelNeed\":").append(safeInt(() -> r.getAlchemyLevelNeed(), 0));
            sb.append('}');
        }
        sb.append("],\"itemToResearch\":{"); boolean firstM = true;
        for (Map.Entry<String, String> e : itemToResearch.entrySet()) {
            if (!firstM) sb.append(','); firstM = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(e.getValue())).append('"');
        }
        sb.append("}}"); return sb.toString();
    }

    private String safeResearchKey(Research r) {
        try {
            if (r == null || r.getKey() == null) return null;
            return r.getKey().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int safeInt(IntGetter getter, int fallback) {
        try { return getter.get(); } catch (Exception e) { return fallback; }
    }

    private boolean safeBoolean(BooleanGetter getter, boolean fallback) {
        try { return getter.get(); } catch (Exception e) { return fallback; }
    }

    private boolean safeResearchEnabled(String fullKey, boolean fallback) {
        try {
            Config config = Slimefun.getResearchCfg();
            if (config == null || fullKey == null) return fallback;
            String[] parts = fullKey.split(":", 2);
            String ns = parts.length > 1 ? parts[0] : "slimefun", k = parts.length > 1 ? parts[1] : parts[0];
            String path = ns + "." + k + ".enabled";
            return config.contains(path) ? config.getBoolean(path) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private double safeDouble(DoubleGetter getter, double fallback) {
        try { return getter.get(); } catch (Exception e) { return fallback; }
    }

    private interface IntGetter { int get(); }

    private interface BooleanGetter { boolean get(); }

    private interface DoubleGetter { double get(); }

    private String safeResearchName(Research r, String fallback) {
        try {
            String name = r.getName(null);
            return name != null && !name.isEmpty() ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<SlimefunItem> safeAffectedItems(Research r) {
        try { return r.getAffectedItems(); } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<SlimefunItem> safeNeedItems(Research r) {
        try { return r.getNeedUnlockedItems(); } catch (Exception e) { return Collections.emptyList(); }
    }

    private String safeItemId(SlimefunItem item) {
        try { return item != null ? item.getId() : null; } catch (Exception e) { return null; }
    }

    private String safeItemName(SlimefunItem item, String fallback) {
        try {
            String name = item.getItemName();
            return name != null && !name.isEmpty() ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean saveResearchesFromJson(String json) {
        Config config = Slimefun.getResearchCfg();
        if (config == null) { plugin.getLogger().warning("Research config null, save aborted"); return false; }
        List<ResearchUpdate> updates;
        try {
            updates = parseResearchSavePayload(json);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid research save payload", e);
            return false;
        }
        String[] previousContent = new String[1];
        try {
            runSync(() -> {
                previousContent[0] = readResearchConfigContent(config);
                for (ResearchUpdate update : updates) applyResearchUpdate(update, config);
                config.save();
                boolean reloaded = Slimefun.getConfigManager().load(true);
                if (!reloaded) throw new IllegalStateException("Slimefun reload returned false");
                plugin.getLogger().info("Researches.yml saved via web editor");
            });
            return true;
        } catch (Exception e) {
            restoreResearchConfig(config, previousContent[0]);
            plugin.getLogger().log(Level.WARNING, "Failed to save Researches.yml via web editor", e);
            return false;
        }
    }

    private String readResearchConfigContent(Config config) {
        try {
            File file = config.getFile();
            if (file == null || !file.exists()) return null;
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to backup Researches.yml before web save", e);
            return null;
        }
    }

    private void restoreResearchConfig(Config config, String previousContent) {
        try {
            File file = config.getFile();
            if (file == null) return;
            if (previousContent == null) java.nio.file.Files.deleteIfExists(file.toPath());
            else java.nio.file.Files.write(file.toPath(), previousContent.getBytes(StandardCharsets.UTF_8));
            config.reload();
            Slimefun.getConfigManager().load(true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore Researches.yml after web save failure", e);
        }
    }

    private void runSync(Runnable task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            task.run();
            return null;
        }).get();
    }

    static List<ResearchUpdate> parseResearchSavePayload(String json) {
        List<ResearchUpdate> updates = new ArrayList<>();
        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject()) return updates;
        JsonObject rootObj = parsed.getAsJsonObject();
        if (!rootObj.has("researches") || !rootObj.get("researches").isJsonArray()) return updates;
        for (JsonElement element : rootObj.getAsJsonArray("researches")) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String fullKey = jsonString(obj, "fullKey");
            if (fullKey == null || fullKey.isEmpty()) continue;
            ResearchUpdate update = new ResearchUpdate(fullKey);
            update.levelCost = jsonInt(obj, "levelCost");
            update.moneyCost = jsonDouble(obj, "moneyCost");
            update.enabled = jsonBoolean(obj, "enabled");
            JsonArray needItems = jsonArray(obj, "needUnlockedItems");
            if (needItems != null) {
                update.needUnlockedItems = new ArrayList<>();
                for (JsonElement needItem : needItems) {
                    if (needItem.isJsonPrimitive()) update.needUnlockedItems.add(needItem.getAsString());
                }
            }
            String[] skillKeys = {"miningLevelNeed", "farmingLevelNeed", "foragingLevelNeed", "fishingLevelNeed", "excavationLevelNeed", "archeryLevelNeed", "defenseLevelNeed", "fightingLevelNeed", "agilityLevelNeed", "enchantingLevelNeed", "alchemyLevelNeed"};
            for (String skillKey : skillKeys) {
                Integer value = jsonInt(obj, skillKey);
                if (value != null) update.skillLevels.put(skillKey, value);
            }
            updates.add(update);
        }
        return updates;
    }

    static class ResearchUpdate {
        final String fullKey;
        Integer levelCost;
        Double moneyCost;
        Boolean enabled;
        List<String> needUnlockedItems;
        final Map<String, Integer> skillLevels = new LinkedHashMap<>();

        ResearchUpdate(String fullKey) {
            this.fullKey = fullKey;
        }
    }

    private void applyResearchUpdate(ResearchUpdate update, Config config) {
        String[] parts = update.fullKey.split(":", 2);
        String ns = parts.length > 1 ? parts[0] : "slimefun", k = parts.length > 1 ? parts[1] : parts[0];
        String path = ns + "." + k;
        if (update.levelCost != null) config.setValue(path + ".levelCost", Math.max(0, update.levelCost));
        if (update.moneyCost != null) config.setValue(path + ".moneyCost", Math.max(0, update.moneyCost));
        if (update.enabled != null) config.setValue(path + ".enabled", update.enabled);
        if (update.needUnlockedItems != null) config.setValue(path + ".need-unlocked-items", update.needUnlockedItems);
        for (Map.Entry<String, Integer> entry : update.skillLevels.entrySet()) {
            config.setValue(path + "." + entry.getKey(), Math.max(0, entry.getValue()));
        }
    }

    private static String jsonString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static Integer jsonInt(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return null;
        try { return obj.get(key).getAsInt(); } catch (Exception e) { return null; }
    }

    private static Double jsonDouble(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return null;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return null; }
    }

    private static Boolean jsonBoolean(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return null;
        try { return obj.get(key).getAsBoolean(); } catch (Exception e) { return null; }
    }

    private static JsonArray jsonArray(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
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
