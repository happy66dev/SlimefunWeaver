package cn.rmc.slimefuncustomguide.web;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.model.CustomCategory;
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

    private final CustomGuidePlugin plugin;
    private final String indexHtml;

    public WebApiHandler(CustomGuidePlugin plugin) {
        this.plugin = plugin;
        this.indexHtml = loadFileFromJar("web/index.html");
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

            if (path.equals("/") || path.equals("/index.html")) {
                serveContent(exchange, indexHtml, "text/html");
            } else if (path.equals("/style.css")) {
                serveContent(exchange, loadFileFromJar("web/style.css"), "text/css");
            } else if (path.equals("/editor.js")) {
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

    private void serveContent(HttpExchange exchange, String content, String contentType) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void handleCategories(HttpExchange exchange, String method) throws IOException {
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
            String body = readBody(exchange);
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

        String content = json.trim();
        if (content.startsWith("{\"categories\":[")) {
            int end = content.lastIndexOf(']');
            if (end >= 0) {
                content = content.substring("{\"categories\":[".length(), end);
            }
            int depth = 0;
            int start = -1;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        String catJson = content.substring(start, i + 1);
                        parseCategoryJson(catJson, root);
                        start = -1;
                    }
                } else if (c == '"' && depth == 0) {
                    i = skipString(content, i);
                }
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
            plugin.getLogger().log(Level.WARNING, "Failed to replace categories.yml", e);
        }
    }

    private void parseCategoryJson(String json, ConfigurationSection parent) {
        String key = extractString(json, "key");
        if (key == null) return;
        String display = extractString(json, "display");
        if (display == null) display = key;
        boolean glow = extractBoolean(json, "glow");
        int page = extractInt(json, "page", 1);
        int slot = extractInt(json, "slot", 10);
        String iconType = extractNestedString(json, "icon", "type");
        String iconId = extractNestedString(json, "icon", "id");

        ConfigurationSection section = parent.createSection(key);
        if (display != null) section.set("display", display);
        ConfigurationSection iconSec = section.createSection("icon");
        iconSec.set("type", iconType != null ? iconType : "VANILLA");
        iconSec.set("id", iconId != null ? iconId : "BOOK");
        section.set("page", page);
        section.set("slot", slot);
        if (glow) section.set("glow", true);

        List<String> lore = extractStringList(json, "lore");
        if (lore != null && !lore.isEmpty()) section.set("lore", lore);

        String itemsJson = extractArray(json, "items");
        if (itemsJson != null && !itemsJson.isEmpty()) {
            List<Object> itemList = new ArrayList<>();
            int depth = 0, istart = -1;
            for (int i = 0; i < itemsJson.length(); i++) {
                char c = itemsJson.charAt(i);
                if (c == '{') {
                    if (depth == 0) istart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && istart >= 0) {
                        String itemJson = itemsJson.substring(istart, i + 1);
                        String type = extractString(itemJson, "type");
                        if ("ITEM".equals(type)) {
                            Map<String, Object> itemMap = new LinkedHashMap<>();
                            itemMap.put("id", extractString(itemJson, "id"));
                            itemMap.put("page", extractInt(itemJson, "page", 1));
                            itemMap.put("slot", extractInt(itemJson, "slot", 10));
                            itemList.add(itemMap);
                        } else if ("PLACEHOLDER".equals(type)) {
                            Map<String, Object> ph = new LinkedHashMap<>();
                            Map<String, Object> phIcon = new LinkedHashMap<>();
                            phIcon.put("type", extractNestedString(itemJson, "icon", "type"));
                            phIcon.put("id", extractNestedString(itemJson, "icon", "id"));
                            Map<String, Object> placeholder = new LinkedHashMap<>();
                            placeholder.put("icon", phIcon);
                            String phDisplay = extractString(itemJson, "display");
                            if (phDisplay != null && !phDisplay.isEmpty()) placeholder.put("display", phDisplay);
                            if (extractBoolean(itemJson, "glow")) placeholder.put("glow", true);
                            List<String> phLore = extractStringList(itemJson, "lore");
                            if (phLore != null && !phLore.isEmpty()) placeholder.put("lore", phLore);
                            placeholder.put("page", extractInt(itemJson, "page", 1));
                            placeholder.put("slot", extractInt(itemJson, "slot", 10));
                            ph.put("placeholder", placeholder);
                            itemList.add(ph);
                        }
                        istart = -1;
                    }
                } else if (c == '"' && depth == 0) {
                    i = skipString(itemsJson, i);
                }
            }
            if (!itemList.isEmpty()) section.set("items", itemList);
        }

        String childrenJson = extractArray(json, "children");
        if (childrenJson != null && !childrenJson.isEmpty()) {
            int depth = 0, cstart = -1;
            for (int i = 0; i < childrenJson.length(); i++) {
                char c = childrenJson.charAt(i);
                if (c == '{') {
                    if (depth == 0) cstart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && cstart >= 0) {
                        parseCategoryJson(childrenJson.substring(cstart, i + 1), section);
                        cstart = -1;
                    }
                } else if (c == '"' && depth == 0) {
                    i = skipString(childrenJson, i);
                }
            }
        }
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    default: sb.append(c); break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractNestedString(String json, String parentKey, String childKey) {
        String search = "\"" + parentKey + "\":{";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        return extractString(json.substring(idx), childKey);
    }

    private static List<String> extractStringList(String json, String key) {
        String search = "\"" + key + "\":[";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        List<String> list = new ArrayList<>();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ']') break;
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < json.length()) {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        char next = json.charAt(i + 1);
                        switch (next) {
                            case '"': sb.append('"'); i++; break;
                            case '\\': sb.append('\\'); i++; break;
                            case 'n': sb.append('\n'); i++; break;
                            case 'r': sb.append('\r'); i++; break;
                            case 't': sb.append('\t'); i++; break;
                            default: sb.append(json.charAt(i)); break;
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

    private static String extractArray(String json, String key) {
        String search = "\"" + key + "\":[";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                if (depth == 0) return json.substring(start, i);
                depth--;
            } else if (c == '"') {
                i = skipString(json, i);
            }
        }
        return null;
    }

    private static boolean extractBoolean(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return false;
        idx += search.length();
        return json.regionMatches(idx, "true", 0, 4);
    }

    private static int extractInt(String json, String key, int defaultVal) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultVal;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') sb.append(c);
            else break;
        }
        if (sb.length() == 0) return defaultVal;
        try { return Integer.parseInt(sb.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static int skipString(String s, int i) {
        i++;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) i += 2;
            else if (s.charAt(i) == '"') break;
            else i++;
        }
        return i;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void handleMaterials(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String q = "";
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("q=")) {
                    q = URLDecoder.decode(param.substring(2), StandardCharsets.UTF_8).toLowerCase();
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

        for (Material mat : Material.values()) {
            if (mat.isItem() && mat.name().toLowerCase().contains(q)) {
                if (!first) sb.append(',');
                sb.append("{\"type\":\"VANILLA\",\"id\":\"").append(mat.name());
                sb.append("\",\"display\":\"").append(JsonUtil.escape(materialName(mat))).append("\"}");
                first = false;
                if (sb.length() > 5000) {
                    truncated = true;
                    break;
                }
            }
        }

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
        StringBuilder sb = new StringBuilder("{\"groups\":[");
        boolean first = true;
        for (io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (!first) sb.append(',');
            sb.append("{\"key\":\"").append(JsonUtil.escape(group.getUnlocalizedName()));
            sb.append("\",\"display\":\"").append(JsonUtil.escape(group.getDisplayName(null))).append("\"}");
            first = false;
        }
        sb.append("]}");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String materialName(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') { sb.append(' '); capitalize = true; }
            else if (capitalize) { sb.append(Character.toUpperCase(c)); capitalize = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

}
