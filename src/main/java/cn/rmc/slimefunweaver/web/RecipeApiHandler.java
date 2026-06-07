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
import cn.rmc.slimefunweaver.util.IconParser;
import cn.rmc.slimefunweaver.util.VanillaMaterialLocalization;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class RecipeApiHandler implements HttpHandler {

    private static SlimefunWeaver plugin;
    private static YamlConfiguration storedRecipes;
    private final String recipesHtml;

    private static final Set<String> BUILTIN_RECIPE_TYPES = new LinkedHashSet<>();
    static {
        BUILTIN_RECIPE_TYPES.add("slimefun:enhanced_crafting_table");
        BUILTIN_RECIPE_TYPES.add("slimefun:armor_forge");
        BUILTIN_RECIPE_TYPES.add("slimefun:grind_stone");
        BUILTIN_RECIPE_TYPES.add("slimefun:smeltery");
        BUILTIN_RECIPE_TYPES.add("slimefun:ore_crusher");
        BUILTIN_RECIPE_TYPES.add("slimefun:compressor");
        BUILTIN_RECIPE_TYPES.add("slimefun:pressure_chamber");
        BUILTIN_RECIPE_TYPES.add("slimefun:magic_workbench");
        BUILTIN_RECIPE_TYPES.add("slimefun:gold_pan");
        BUILTIN_RECIPE_TYPES.add("slimefun:juicer");
        BUILTIN_RECIPE_TYPES.add("slimefun:ancient_altar");
        BUILTIN_RECIPE_TYPES.add("slimefun:heated_pressure_chamber");
        BUILTIN_RECIPE_TYPES.add("slimefun:ore_washer");
        BUILTIN_RECIPE_TYPES.add("slimefun:table_saw");
        BUILTIN_RECIPE_TYPES.add("slimefun:freezer");
        BUILTIN_RECIPE_TYPES.add("slimefun:food_fabricator");
        BUILTIN_RECIPE_TYPES.add("slimefun:food_composter");
        BUILTIN_RECIPE_TYPES.add("slimefun:reactor");
        BUILTIN_RECIPE_TYPES.add("slimefun:refinery");
        BUILTIN_RECIPE_TYPES.add("slimefun:automated_panning_machine");
        BUILTIN_RECIPE_TYPES.add("slimefun:miner_android");
        BUILTIN_RECIPE_TYPES.add("slimefun:fisherman_android");
        BUILTIN_RECIPE_TYPES.add("slimefun:geo_miner");
        BUILTIN_RECIPE_TYPES.add("slimefun:oil_pump");
        BUILTIN_RECIPE_TYPES.add("slimefun:nuclear_reactor");
    }

    private static final Set<String> TIMED_RECIPE_TYPES = new HashSet<>();
    static {
        TIMED_RECIPE_TYPES.add("slimefun:smeltery");
        TIMED_RECIPE_TYPES.add("slimefun:ore_crusher");
        TIMED_RECIPE_TYPES.add("slimefun:compressor");
        TIMED_RECIPE_TYPES.add("slimefun:pressure_chamber");
        TIMED_RECIPE_TYPES.add("slimefun:heated_pressure_chamber");
        TIMED_RECIPE_TYPES.add("slimefun:grind_stone");
        TIMED_RECIPE_TYPES.add("slimefun:juicer");
        TIMED_RECIPE_TYPES.add("slimefun:freezer");
        TIMED_RECIPE_TYPES.add("slimefun:food_fabricator");
        TIMED_RECIPE_TYPES.add("slimefun:food_composter");
        TIMED_RECIPE_TYPES.add("slimefun:reactor");
        TIMED_RECIPE_TYPES.add("slimefun:refinery");
        TIMED_RECIPE_TYPES.add("slimefun:oil_pump");
        TIMED_RECIPE_TYPES.add("slimefun:geo_miner");
        TIMED_RECIPE_TYPES.add("slimefun:miner_android");
        TIMED_RECIPE_TYPES.add("slimefun:fisherman_android");
        TIMED_RECIPE_TYPES.add("slimefun:ore_washer");
        TIMED_RECIPE_TYPES.add("slimefun:table_saw");
        TIMED_RECIPE_TYPES.add("slimefun:nuclear_reactor");
    }

    public RecipeApiHandler(SlimefunWeaver plugin) {
        RecipeApiHandler.plugin = plugin;
        this.recipesHtml = loadFileFromJar("web/recipes.html");
    }

    private void redirectToLogin(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().set("Location", "/?redirect=" + path.substring(1));
        exchange.sendResponseHeaders(302, -1);
    }

    public static void loadRecipesOnStartup(SlimefunWeaver plugin) {
        RecipeApiHandler.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "Recipes.yml");
        if (!file.exists()) return;
        try {
            storedRecipes = YamlConfiguration.loadConfiguration(file);
            applyAllRecipes();
            plugin.getLogger().info("Loaded Recipes.yml with stored customizations");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load Recipes.yml on startup", e);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/recipes.html")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { redirectToLogin(exchange); return; }
                serveHtml(exchange, recipesHtml);
            } else if (path.equals("/api/recipes")) {
                handleRecipes(exchange, method);
            } else if (path.equals("/api/recipes/materials")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                handleMaterials(exchange);
            } else if (path.equals("/api/recipe-types")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                serveJson(exchange, buildRecipeTypesJson());
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Recipe API error", e);
            try { exchange.sendResponseHeaders(500, -1); } catch (IOException ignored) {}
        }
    }

    private void handleMaterials(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String q = "";
        if (query != null) {
            for (String p : query.split("&")) {
                if (p.startsWith("q=")) q = URLDecoder.decode(p.substring(2), "UTF-8").toLowerCase(Locale.ROOT).trim();
            }
        }
        if (plugin.isDebugEnabled()) plugin.getLogger().info("[RecipeAPI] materials search q=" + q);
        StringBuilder sb = new StringBuilder("{\"results\":[");
        boolean first = true;
        int count = 0;
        int max = q.isEmpty() ? 0 : 80;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            String id = item.getId().toLowerCase(Locale.ROOT);
            String name = item.getItemName();
            String nameLower = name != null ? org.bukkit.ChatColor.stripColor(name).toLowerCase(Locale.ROOT) : "";
            if (!q.isEmpty() && !id.contains(q) && !nameLower.contains(q)) continue;
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"SLIMEFUN\",\"id\":\"").append(escapeJson(item.getId())).append("\",\"display\":\"")
              .append(escapeJson(nameLower.isEmpty() ? item.getId() : org.bukkit.ChatColor.stripColor(name)))
              .append("\"}");
            count++;
        }

        for (Material mat : Material.values()) {
            if (!mat.isItem() || mat.isAir() || mat.isLegacy()) continue;
            String matName = mat.name().toLowerCase(Locale.ROOT);
            String displayName = VanillaMaterialLocalization.getItemName(mat);
            String displayLower = displayName.toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !matName.contains(q) && !displayLower.contains(q)) continue;
            if (count >= max) break;
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"VANILLA\",\"id\":\"").append(escapeJson(mat.name())).append("\",\"display\":\"")
              .append(escapeJson(displayName)).append("\"}");
            count++;
        }
        sb.append("]}");
        serveJson(exchange, sb.toString());
    }

    private String loadFileFromJar(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return "<h1>File not found: " + path + "</h1>";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { return "<h1>Error: " + e.getMessage() + "</h1>"; }
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

    private void handleRecipes(HttpExchange exchange, String method) throws IOException {
        if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
        if ("GET".equalsIgnoreCase(method)) {
            serveJson(exchange, buildRecipesJson());
        } else if ("PUT".equalsIgnoreCase(method)) {
            if (!WebSecurity.isWriteAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
            String body;
            try { body = WebSecurity.readBody(exchange); }
            catch (WebSecurity.BodyTooLargeException e) { exchange.sendResponseHeaders(413, -1); return; }
            if (body == null || body.isEmpty()) { exchange.sendResponseHeaders(400, -1); return; }
            saveRecipesFromJson(body);
            serveJson(exchange, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private String buildRecipeTypesJson() {
        StringBuilder sb = new StringBuilder("{\"types\":[");
        boolean first = true;
        Map<String, RecipeType> resolved = resolveBuiltinTypes();

        for (String key : BUILTIN_RECIPE_TYPES) {
            if (!first) sb.append(','); first = false;
            RecipeType rt = resolved.get(key);
            String name = key;
            int slots = guessSlots(key);
            int cols = guessCols(slots);
            if (rt != null) {
                name = readDisplayName(rt, key);
            }
            sb.append("{\"key\":\"").append(escapeJson(key)).append("\",")
              .append("\"name\":\"").append(escapeJson(name)).append("\",")
              .append("\"slots\":").append(slots).append(",")
              .append("\"cols\":").append(cols).append(",")
              .append("\"hasTime\":").append(TIMED_RECIPE_TYPES.contains(key))
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildRecipesJson() {
        Map<String, RecipeType> resolved = resolveBuiltinTypes();

        StringBuilder sb = new StringBuilder("{\"items\":{");
        boolean firstItem = true;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            String id = item.getId();
            ItemStack[] recipe = item.getRecipe();
            if (recipe == null || recipe.length == 0) continue;

            if (!firstItem) sb.append(','); firstItem = false;

            sb.append('"').append(escapeJson(id)).append("\":{");
            appendString(sb, "id", id);
            sb.append(',');
            appendString(sb, "name", item.getItemName());
            sb.append(',');

            String rtKey = item.getRecipeType() != null ? item.getRecipeType().getKey().toString() : null;
            sb.append("\"currentRecipeType\":");
            sb.append(rtKey != null ? '"' + escapeJson(rtKey) + '"' : "null");
            sb.append(',');

            sb.append("\"currentRecipe\":[");
            for (int i = 0; i < recipe.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJson(itemIdFromStack(recipe[i]))).append('"');
            }
            sb.append("],");

            sb.append("\"recipes\":[");
            boolean hasStored = false;
            if (storedRecipes != null) {
                StoredRecipesSection parsed = parseRecipes(storedRecipes, id);
                List<Map<?, ?>> recipes = parsed.recipes;
                if (recipes != null) {
                    for (Map<?, ?> r : recipes) {
                        if (hasStored) sb.append(',');
                        hasStored = true;
                        sb.append(recipeToJson(r, id));
                    }
                }
            }
            if (!hasStored && rtKey != null) {
                sb.append(defaultRecipeJson(id, rtKey, recipe));
            }
            sb.append(']');
            sb.append('}');
        }

        sb.append("},\"recipeTypes\":[");
        boolean firstType = true;
        for (String key : BUILTIN_RECIPE_TYPES) {
            if (!firstType) sb.append(','); firstType = false;
            RecipeType rt = resolved.get(key);
            String name = key;
            int slots = guessSlots(key);
            int cols = guessCols(slots);
            if (rt != null) name = readDisplayName(rt, key);
            sb.append("{\"key\":\"").append(escapeJson(key)).append("\",")
              .append("\"name\":\"").append(escapeJson(name)).append("\",")
              .append("\"slots\":").append(slots).append(",")
              .append("\"cols\":").append(cols).append(",")
              .append("\"hasTime\":").append(TIMED_RECIPE_TYPES.contains(key))
              .append("}");
        }
        sb.append("]}");

        return sb.toString();
    }

    private String defaultRecipeJson(String itemId, String rtKey, ItemStack[] recipe) {
        List<String> inputIds = new ArrayList<>();
        for (ItemStack stack : recipe) inputIds.add(itemIdFromStack(stack));
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(escapeJson(rtKey)).append("\",")
          .append("\"input\":[").append(jsonStringListCast(inputIds)).append("],")
          .append("\"output\":\"").append(escapeJson(itemId)).append("\",")
          .append("\"outputAmount\":1,\"processingTime\":0}");
        return sb.toString();
    }

    private String recipeToJson(Map<?, ?> r, String fallbackId) {
        String type = String.valueOf(r.get("type"));
        List<?> inputList = (List<?>) r.get("input");
        String output = String.valueOf(r.get("output"));
        int amount = toInt(r.get("output-amount"), 1);
        int time = toInt(r.get("processing-time"), 0);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(escapeJson(type)).append("\",")
          .append("\"input\":[").append(jsonStringList(inputList)).append("],")
          .append("\"output\":\"").append(escapeJson(output)).append("\",")
          .append("\"outputAmount\":").append(amount).append(",")
          .append("\"processingTime\":").append(time).append("}");
        return sb.toString();
    }

    private static Map<String, RecipeType> recipeTypeCache = Collections.emptyMap();

    private static Map<String, RecipeType> resolveBuiltinTypes() {
        Map<String, RecipeType> map = new LinkedHashMap<>();
        for (Field f : RecipeType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
            if (!RecipeType.class.isAssignableFrom(f.getType())) continue;
            try {
                RecipeType rt = (RecipeType) f.get(null);
                String k = rt.getKey().toString();
                if (BUILTIN_RECIPE_TYPES.contains(k)) map.put(k, rt);
            } catch (Exception ignored) {}
        }
        recipeTypeCache = map;
        return map;
    }

    private static class StoredRecipesSection {
        final ConfigurationSection section;
        final List<Map<?, ?>> recipes;
        StoredRecipesSection(ConfigurationSection s, List<Map<?, ?>> r) { section = s; recipes = r; }
    }

    private static StoredRecipesSection parseRecipes(YamlConfiguration yaml, String itemId) {
        ConfigurationSection sec = yaml.getConfigurationSection("slimefun." + itemId);
        if (sec == null) return new StoredRecipesSection(null, null);
        List<?> raw = sec.getList("recipes");
        if (raw == null) return new StoredRecipesSection(sec, null);
        List<Map<?, ?>> list = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Map) list.add((Map<?, ?>) entry);
        }
        return new StoredRecipesSection(sec, list);
    }

    private void saveRecipesFromJson(String json) {
        YamlConfiguration yaml;
        File finalFile = new File(plugin.getDataFolder(), "Recipes.yml");
        if (finalFile.exists()) {
            yaml = YamlConfiguration.loadConfiguration(finalFile);
        } else {
            yaml = new YamlConfiguration();
        }
        ConfigurationSection root;
        if (yaml.isConfigurationSection("slimefun")) {
            root = yaml.getConfigurationSection("slimefun");
        } else {
            root = yaml.createSection("slimefun");
        }

        String content = json.trim();
        if (!content.startsWith("{\"items\":{")) return;
        int start = "{\"items\":{".length();
        int depth = 1, end = -1;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') { depth--; if (depth == 0) { end = i; break; } }
            else if (ch == '"') i = skipJsonString(content, i);
        }
        if (end <= start) return;
        String c = content.substring(start, end);

        depth = 0; int objStart = -1;
        String currentItemId = null;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch == '{') {
                depth++;
                if (depth == 1) objStart = i;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0 && currentItemId != null) {
                    applyRecipeSave(c.substring(objStart, i + 1), currentItemId, root);
                    objStart = -1;
                    currentItemId = null;
                }
            } else if (ch == '"' && depth == 0) {
                int keyEnd = skipJsonString(c, i);
                currentItemId = extractSimpleString(c.substring(i, keyEnd + 1));
                i = keyEnd;
                while (i + 1 < c.length() && c.charAt(i + 1) != '{') i++;
            }
        }

        File tempFile = new File(plugin.getDataFolder(), "Recipes.yml.tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            writer.write(yaml.saveToString());
            writer.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save Recipes.yml", e);
            return;
        }
        try {
            java.nio.file.Files.move(tempFile.toPath(), finalFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to replace Recipes.yml", e);
        }

        storedRecipes = yaml;
        applyAllRecipes();

        plugin.getLogger().info("Recipes.yml saved and applied");
    }

    private void applyRecipeSave(String json, String itemId, ConfigurationSection parent) {
        String recipesJson = extractJsonArray(json, "recipes");
        if (recipesJson == null) return;

        parent.set(itemId, null);
        ConfigurationSection itemSec = parent.createSection(itemId);
        List<Map<String, Object>> recipeList = new ArrayList<>();

        int depth = 0, objStart = -1;
        for (int i = 0; i < recipesJson.length(); i++) {
            char ch = recipesJson.charAt(i);
            if (ch == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String rj = recipesJson.substring(objStart, i + 1);
                    Map<String, Object> map = new LinkedHashMap<>();
                    String type = extractJsonStringSimple(rj, "type");
                    if (type != null) map.put("type", type);
                    List<String> input = extractJsonStringArray(rj, "input");
                    if (input != null) map.put("input", input);
                    String output = extractJsonStringSimple(rj, "output");
                    if (output != null) map.put("output", output);
                    Integer outAmt = extractJsonInt(rj, "outputAmount");
                    map.put("output-amount", outAmt != null ? outAmt : 1);
                    Integer time = extractJsonInt(rj, "processingTime");
                    if (time != null && time > 0) map.put("processing-time", time);
                    recipeList.add(map);
                    objStart = -1;
                }
            } else if (ch == '"' && depth == 0) {
                i = skipJsonString(recipesJson, i);
            }
        }
        itemSec.set("recipes", recipeList);
    }

    private static void applyAllRecipes() {
        if (storedRecipes == null) return;

        ConfigurationSection root = storedRecipes.getConfigurationSection("slimefun");
        if (root == null) return;

        Map<String, RecipeType> resolved = resolveBuiltinTypes();

        for (String itemId : root.getKeys(false)) {
            SlimefunItem item = IconParser.findSlimefunItem(itemId);
            if (item == null) continue;

            StoredRecipesSection parsed = parseRecipes(storedRecipes, itemId);
            List<Map<?, ?>> recipes = parsed.recipes;
            if (recipes == null || recipes.isEmpty()) continue;

            Map<?, ?> first = recipes.get(0);
            String typeKey = String.valueOf(first.get("type"));
            RecipeType rt = findRecipeTypeByKey(typeKey, resolved);
            if (rt == null) continue;

            Object inputObj = first.get("input");
            if (!(inputObj instanceof List)) continue;
            List<?> inputList = (List<?>) inputObj;
            Object outputVal = first.get("output");
            int outputAmount = clamp(toInt(first.get("output-amount"), 1), 1, 64);

            ItemStack[] inputStacks = new ItemStack[inputList.size()];
            for (int i = 0; i < inputList.size(); i++) {
                String matId = String.valueOf(inputList.get(i));
                inputStacks[i] = resolveItemStack(matId);
            }

            String outId = outputVal != null ? String.valueOf(outputVal) : itemId;
            ItemStack outputStack = resolveItemStack(outId);
            outputStack.setAmount(outputAmount);

            item.setRecipeType(rt);
            item.setRecipe(inputStacks);
            item.setRecipeOutput(outputStack);

            int processingTime = toInt(first.get("processing-time"), 0);
            if (processingTime > 0 && TIMED_RECIPE_TYPES.contains(typeKey)) {
                try {
                    Method setProcessingTime = item.getClass().getMethod("setProcessingTime", int.class);
                    setProcessingTime.invoke(item, processingTime);
                } catch (Exception ignored) {}
            }
        }

        plugin.getLogger().info("Recipes applied in real-time");
    }

    private static ItemStack resolveItemStack(String id) {
        if (id == null || id.isEmpty() || "AIR".equalsIgnoreCase(id)) return new ItemStack(Material.AIR);
        Material mat = Material.matchMaterial(id);
        if (mat != null) return new ItemStack(mat);
        SlimefunItem sfItem = IconParser.findSlimefunItem(id);
        if (sfItem != null && sfItem.getRecipeOutput() != null) return sfItem.getRecipeOutput().clone();
        plugin.getLogger().warning("Unknown recipe item ID, using AIR: " + id);
        return new ItemStack(Material.AIR);
    }

    private String itemIdFromStack(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return "AIR";
        SlimefunItem sfItem = SlimefunItem.getByItem(stack);
        if (sfItem != null) return sfItem.getId();
        return stack.getType().name();
    }

    private static RecipeType findRecipeTypeByKey(String key, Map<String, RecipeType> resolved) {
        RecipeType cached = resolved.get(key);
        if (cached != null) return cached;

        for (Field f : RecipeType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
            if (!RecipeType.class.isAssignableFrom(f.getType())) continue;
            try {
                RecipeType rt = (RecipeType) f.get(null);
                if (rt.getKey().toString().equals(key)) return rt;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String readDisplayName(RecipeType rt, String key) {
        try {
            ItemStack rtItem = rt.toItem();
            if (rtItem != null && rtItem.hasItemMeta() && rtItem.getItemMeta().hasLore()) {
                for (String lore : rtItem.getItemMeta().getLore()) {
                    if (lore != null && !lore.trim().isEmpty())
                        return org.bukkit.ChatColor.stripColor(lore.trim());
                }
            }
        } catch (Exception ignored) {}
        String shortKey = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
        return shortKey.replace('_', ' ');
    }

    private static int guessSlots(String key) {
        String shortKey = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
        switch (shortKey) {
            case "enhanced_crafting_table": case "armor_forge": case "magic_workbench":
            case "ancient_altar": return 9;
            case "smeltery": case "heated_pressure_chamber": case "ore_crusher":
            case "compressor": case "grind_stone": case "juicer": case "gold_pan":
            case "freezer": case "food_fabricator": case "food_composter":
            case "reactor": case "refinery": case "pressure_chamber": case "table_saw":
                return 2;
            default: return 1;
        }
    }

    private static int guessCols(int slots) {
        if (slots == 9) return 3;
        if (slots == 2) return 2;
        return 1;
    }

    private static String extractJsonArray(String json, String key) {
        String s = "\"" + key + "\":["; int idx = json.indexOf(s);
        if (idx < 0) return null;
        int start = idx + s.length(), depth = 0;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') { if (depth == 0) return json.substring(start, i); depth--; }
            else if (ch == '"') i = skipJsonString(json, i);
        }
        return null;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        String s = "\"" + key + "\":["; int idx = json.indexOf(s);
        if (idx < 0) return new ArrayList<>();
        int start = idx + s.length(); List<String> list = new ArrayList<>();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i); if (ch == ']') break;
            if (ch == '"') { StringBuilder sb = new StringBuilder(); i++;
                while (i < json.length()) {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        char nx = json.charAt(i + 1);
                        if (nx == '"') { sb.append('"'); i++; } else if (nx == '\\') { sb.append('\\'); i++; }
                        else sb.append(json.charAt(i));
                    } else if (json.charAt(i) == '"') break; else sb.append(json.charAt(i));
                    i++;
                }
                list.add(sb.toString());
            }
        }
        return list;
    }

    private static String extractJsonStringSimple(String json, String key) {
        String s = "\"" + key + "\":\""; int idx = json.indexOf(s);
        if (idx < 0) return null;
        int start = idx + s.length(); StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) { i++; continue; }
            if (ch == '"') break;
            sb.append(ch);
        }
        return sb.toString();
    }

    private static Integer extractJsonInt(String json, String key) {
        String s = "\"" + key + "\":"; int idx = json.indexOf(s);
        if (idx < 0) return null;
        idx += s.length();
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < json.length(); i++) {
            char ch = json.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '-') sb.append(ch); else break;
        }
        if (sb.length() == 0) return null;
        try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static int toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) try { return Integer.parseInt((String) v); } catch (NumberFormatException e) {}
        return def;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String extractSimpleString(String quoted) { return quoted.substring(1, quoted.length() - 1); }

    private static int skipJsonString(String s, int i) {
        i++; while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) i += 2;
            else if (s.charAt(i) == '"') break;
            else i++;
        }
        return i;
    }

    private static String jsonStringList(List<?> list) {
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : list) {
            if (!first) sb.append(','); first = false;
            sb.append('"').append(escapeJson(String.valueOf(o))).append('"');
        }
        return sb.toString();
    }

    private static String jsonStringListCast(List<String> list) {
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : list) {
            if (!first) sb.append(','); first = false;
            sb.append('"').append(escapeJson(s)).append('"');
        }
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(escapeJson(value != null ? value : "")).append('"');
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder o = new StringBuilder();
        for (char ch : s.toCharArray()) switch (ch) {
            case '"': o.append("\\\""); break;
            case '\\': o.append("\\\\"); break;
            case '\n': o.append("\\n"); break;
            case '\r': o.append("\\r"); break;
            case '\t': o.append("\\t"); break;
            default: o.append(ch);
        }
        return o.toString();
    }
}
