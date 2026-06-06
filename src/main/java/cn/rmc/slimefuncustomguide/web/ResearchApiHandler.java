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
            if (in == null) {
                return "<h1>File not found: " + path + "</h1>";
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<h1>Error: " + e.getMessage() + "</h1>";
        }
    }

    private void serveHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void serveJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void handleResearches(HttpExchange exchange, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            serveJson(exchange, buildResearchesJson());
        } else if ("PUT".equalsIgnoreCase(method)) {
            String body = readBody(exchange);
            if (body == null || body.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            saveResearchesFromJson(body);
            serveJson(exchange, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleSlimefunItems(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String q = "";
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("q=")) {
                    try {
                        q = java.net.URLDecoder.decode(param.substring(2), "UTF-8").toLowerCase();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");

        List<SlimefunItem> list;
        if (!q.isEmpty()) {
            list = new ArrayList<>();
            for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
                String name = item.getItemName();
                String id = item.getId();
                if ((name != null && name.toLowerCase().contains(q))
                        || id.toLowerCase().contains(q)) {
                    list.add(item);
                }
            }
        } else {
            list = Slimefun.getRegistry().getEnabledSlimefunItems();
        }

        boolean first = true;
        for (SlimefunItem item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{');
            appendString(sb, "id", item.getId());
            sb.append(',');
            appendString(sb, "name", item.getItemName());
            sb.append(',');
            Research r = item.getResearch();
            sb.append("\"research\":");
            if (r != null) {
                sb.append('"').append(escapeJson(r.getKey().toString())).append('"');
            } else {
                sb.append("null");
            }
            sb.append(',');
            ItemGroup group = item.getItemGroup();
            sb.append("\"group\":");
            if (group != null) {
                sb.append('"').append(escapeJson(group.getUnlocalizedName())).append('"');
            } else {
                sb.append("null");
            }
            sb.append('}');
        }

        sb.append("]}");
        serveJson(exchange, sb.toString());
    }

    private String buildResearchesJson() {
        Map<String, String> itemToResearch = new LinkedHashMap<>();
        for (Research r : Slimefun.getRegistry().getResearches()) {
            if (!r.isEnabled()) {
                continue;
            }
            for (SlimefunItem item : r.getAffectedItems()) {
                itemToResearch.put(item.getId(), r.getKey().toString());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"researches\":[");
        boolean firstResearch = true;

        for (Research r : Slimefun.getRegistry().getResearches()) {
            if (!firstResearch) {
                sb.append(',');
            }
            firstResearch = false;

            String fullKey = r.getKey().toString();
            String[] parts = fullKey.split(":", 2);
            String namespace = parts.length > 1 ? parts[0] : "slimefun";
            String key = parts.length > 1 ? parts[1] : parts[0];

            sb.append('{');
            appendString(sb, "namespace", namespace);
            sb.append(',');
            appendString(sb, "key", key);
            sb.append(',');
            appendString(sb, "fullKey", fullKey);
            sb.append(',');
            appendString(sb, "name", r.getName(null));
            sb.append(',');
            sb.append("\"levelCost\":").append(r.getLevelCost()).append(',');
            sb.append("\"moneyCost\":").append(r.getMoneyCost()).append(',');
            sb.append("\"enabled\":").append(r.isEnabled()).append(',');

            List<SlimefunItem> items = r.getAffectedItems();
            sb.append("\"items\":[");
            boolean firstItem = true;
            for (SlimefunItem item : items) {
                if (!firstItem) {
                    sb.append(',');
                }
                firstItem = false;
                sb.append('{');
                appendString(sb, "id", item.getId());
                sb.append(',');
                appendString(sb, "name", item.getItemName());
                sb.append('}');
            }
            sb.append("],");

            List<SlimefunItem> needItems = r.getNeedUnlockedItems();
            sb.append("\"needUnlockedItems\":[");
            boolean firstNeed = true;
            for (SlimefunItem item : r.getNeedUnlockedItems()) {
                if (!firstNeed) {
                    sb.append(',');
                }
                firstNeed = false;
                sb.append('"').append(escapeJson(item.getId())).append('"');
            }
            sb.append("],");

            sb.append("\"parents\":[");
            boolean firstParent = true;
            for (SlimefunItem item : needItems) {
                String parentKey = itemToResearch.get(item.getId());
                if (parentKey != null) {
                    if (!firstParent) {
                        sb.append(',');
                    }
                    firstParent = false;
                    sb.append('"').append(escapeJson(parentKey)).append('"');
                }
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

        sb.append("],\"itemToResearch\":{");
        boolean firstMapping = true;
        for (Map.Entry<String, String> entry : itemToResearch.entrySet()) {
            if (!firstMapping) {
                sb.append(',');
            }
            firstMapping = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":\"");
            sb.append(escapeJson(entry.getValue())).append('"');
        }
        sb.append("}}");

        return sb.toString();
    }

    private void saveResearchesFromJson(String json) {
        String content = json.trim();
        if (!content.startsWith("{\"researches\":[")) {
            return;
        }

        int start = "{\"researches\":[".length();
        int end = content.lastIndexOf("]}");
        if (end < start) {
            return;
        }
        content = content.substring(start, end);

        Config config = Slimefun.getResearchCfg();
        if (config == null) {
            plugin.getLogger().warning("Research config is null, save aborted");
            return;
        }

        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    objStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    applyResearchUpdate(content.substring(objStart, i + 1), config);
                    objStart = -1;
                }
            } else if (c == '"') {
                i = skipJsonString(content, i);
            }
        }

        config.save();
        plugin.getLogger().info("Researches.yml saved via web editor");
    }

    private void applyResearchUpdate(String json, Config config) {
        String fullKey = extractJsonString(json, "fullKey");
        if (fullKey == null) {
            return;
        }

        String[] parts = fullKey.split(":", 2);
        String namespace = parts.length > 1 ? parts[0] : "slimefun";
        String key = parts.length > 1 ? parts[1] : parts[0];
        String path = namespace + "." + key;

        Integer levelCost = extractJsonInt(json, "levelCost");
        if (levelCost != null) {
            config.setValue(path + ".levelCost", levelCost);
        }

        Integer moneyCost = extractJsonInt(json, "moneyCost");
        if (moneyCost != null) {
            config.setValue(path + ".moneyCost", moneyCost);
        }

        Boolean enabled = extractJsonBoolean(json, "enabled");
        if (enabled != null) {
            config.setValue(path + ".enabled", enabled);
        }

        List<String> needItems = extractJsonStringArray(json, "needUnlockedItems");
        if (needItems != null) {
            config.setValue(path + ".need-unlocked-items", needItems);
        }

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

    private static void setIfInt(Config config, String path, Integer value) {
        if (value != null) {
            config.setValue(path, value);
        }
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\":null";
            idx = json.indexOf(search);
            if (idx >= 0) {
                return null;
            }
            return null;
        }
        int start = idx + search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Integer extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        idx += search.length();
        if (json.regionMatches(idx, "null", 0, 4)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                sb.append(c);
            } else {
                break;
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean extractJsonBoolean(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        idx += search.length();
        if (json.regionMatches(idx, "true", 0, 4)) {
            return true;
        }
        if (json.regionMatches(idx, "false", 0, 5)) {
            return false;
        }
        return null;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        String search = "\"" + key + "\":[";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        int start = idx + search.length();
        List<String> list = new ArrayList<>();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ']') {
                break;
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < json.length()) {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        char next = json.charAt(i + 1);
                        if (next == '"') {
                            sb.append('"');
                            i++;
                        } else if (next == '\\') {
                            sb.append('\\');
                            i++;
                        } else if (next == 'n') {
                            sb.append('\n');
                            i++;
                        } else {
                            sb.append(json.charAt(i));
                        }
                    } else if (json.charAt(i) == '"') {
                        break;
                    } else {
                        sb.append(json.charAt(i));
                    }
                    i++;
                }
                list.add(sb.toString());
            }
        }
        return list;
    }

    private static int skipJsonString(String s, int i) {
        i++;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                i += 2;
            } else if (s.charAt(i) == '"') {
                break;
            } else {
                i++;
            }
        }
        return i;
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"");
        sb.append(escapeJson(value != null ? value : ""));
        sb.append('"');
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
