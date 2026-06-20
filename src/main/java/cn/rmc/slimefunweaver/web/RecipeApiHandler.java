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
import cn.rmc.slimefunweaver.util.ColorUtil;
import cn.rmc.slimefunweaver.util.IconParser;
import cn.rmc.slimefunweaver.util.VanillaMaterialLocalization;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeEntry;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
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
    private static volatile YamlConfiguration storedRecipes;
    private static final Map<String, RecipeSnapshot> originalRecipes = new LinkedHashMap<>();
    private final String recipesHtml;

    private static final Set<String> EDITABLE_RECIPE_TYPES = new LinkedHashSet<>();
    static {
        EDITABLE_RECIPE_TYPES.add("slimefun:enhanced_crafting_table");
        EDITABLE_RECIPE_TYPES.add("slimefun:magic_workbench");
        EDITABLE_RECIPE_TYPES.add("slimefun:armor_forge");
        EDITABLE_RECIPE_TYPES.add("slimefun:ancient_altar");
        EDITABLE_RECIPE_TYPES.add("slimefun:ore_crusher");
        EDITABLE_RECIPE_TYPES.add("slimefun:grind_stone");
        EDITABLE_RECIPE_TYPES.add("slimefun:compressor");
        EDITABLE_RECIPE_TYPES.add("slimefun:smeltery");
        EDITABLE_RECIPE_TYPES.add("slimefun:juicer");
        EDITABLE_RECIPE_TYPES.add("slimefun:heated_pressure_chamber");
        EDITABLE_RECIPE_TYPES.add("slimefun:food_fabricator");
        EDITABLE_RECIPE_TYPES.add("slimefun:food_composter");
        EDITABLE_RECIPE_TYPES.add("slimefun:refinery");
        EDITABLE_RECIPE_TYPES.add("slimefun:freezer");
        // 喵~以下三个原来漏掉了，均是 MultiBlock 机器，register() 走 mbm.addRecipe() 支持编辑喵
        // 注意：gold_pan / ore_washer 输出随机，不加入可编辑列表喵
        EDITABLE_RECIPE_TYPES.add("slimefun:pressure_chamber");
    }

    private static final Map<String, Integer> ADDON_RECIPE_TYPE_SLOTS = new LinkedHashMap<>();
    private boolean addonDetected = false;

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
        this.recipesHtml = WebApiHandler.loadFileFromJar(plugin, "web/recipes.html");
    }

    private void redirectToLogin(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().set("Location", "/?redirect=" + path.substring(1));
        exchange.sendResponseHeaders(302, -1);
    }

    public static void loadRecipesOnStartup(SlimefunWeaver plugin) {
        RecipeApiHandler.plugin = plugin;
        captureOriginalRecipes();
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
        if (q.isEmpty()) {
            serveJson(exchange, "{\"results\":[]}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"results\":[");
        boolean first = true;
        int count = 0;
        int max = 80;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            String id = item.getId().toLowerCase(Locale.ROOT);
            String name = item.getItemName();
            String nameLower = name != null ? ColorUtil.stripColorCodes(name).toLowerCase(Locale.ROOT) : "";
            if (!q.isEmpty() && !id.contains(q) && !nameLower.contains(q)) continue;
            if (count >= max) break;
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"SLIMEFUN\",\"id\":\"").append(escapeJson(item.getId())).append("\",\"display\":\"")
              .append(escapeJson(nameLower.isEmpty() ? item.getId() : ColorUtil.stripColorCodes(name)))
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
            if (!saveRecipesFromJson(body)) { exchange.sendResponseHeaders(500, -1); return; }
            serveJson(exchange, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private String buildRecipeTypesJson() {
        Map<String, RecipeType> resolved = collectRuntimeRecipeTypes(resolveBuiltinTypes());
        Map<String, RecipeTypeInfo> collected = collectAllRecipeTypes(resolved);
        StringBuilder sb = new StringBuilder("{\"types\":[");
        boolean first = true;
        for (RecipeTypeInfo info : collected.values()) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"key\":\"").append(escapeJson(info.key)).append("\",")
              .append("\"name\":\"").append(escapeJson(info.name)).append("\",")
              .append("\"slots\":").append(info.slots).append(",")
              .append("\"cols\":").append(info.cols).append(",")
              .append("\"hasTime\":").append(info.hasTime).append(",")
              .append("\"isBuiltin\":").append(info.isBuiltin).append(",")
              .append("\"isEditable\":").append(info.isEditable)
              .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void detectAddonRecipeTypes(Map<String, RecipeType> resolved) {
        if (addonDetected) return;
        for (Map.Entry<String, RecipeType> entry : resolved.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("galactifun:") && key.contains("assembly")) {
                EDITABLE_RECIPE_TYPES.add(key);
                ADDON_RECIPE_TYPE_SLOTS.put(key, 9);
            } else if (key.startsWith("infinityexpansion:") && (key.contains("storage") || key.contains("infinity"))) {
                EDITABLE_RECIPE_TYPES.add(key);
                ADDON_RECIPE_TYPE_SLOTS.put(key, 9);
            }
        }
        addonDetected = true;
    }

    private Map<String, RecipeTypeInfo> collectAllRecipeTypes(Map<String, RecipeType> resolved) {
        detectAddonRecipeTypes(resolved);
        Map<String, RecipeTypeInfo> map = new LinkedHashMap<>();

        // 喵~EDITABLE_RECIPE_TYPES 里的都是 SF4 自带且可编辑的类型喵
        for (String key : EDITABLE_RECIPE_TYPES) {
            RecipeType rt = resolved.get(key);
            String name = readDisplayName(rt, key);
            int slots = guessSlots(key);
            map.put(key, new RecipeTypeInfo(key, name, slots, guessCols(slots),
                TIMED_RECIPE_TYPES.contains(key), true, true));
        }

        // 喵~遍历物品收集未在上方出现的配方类型；用 namespace 判断是否为 SF4 自带喵
        // slimefun: 命名空间 = SF4 内置（isBuiltin=true），其余 = addon（isBuiltin=false）
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            ItemStack[] recipe = item.getRecipe();
            if (recipe == null || recipe.length == 0) continue;
            RecipeType rt = item.getRecipeType();
            if (rt == null) continue;
            String key = rt.getKey().toString();
            if (map.containsKey(key)) continue;

            String name = readDisplayName(rt, key);
            int slots = guessSlots(key);
            // 喵~namespace=slimefun 的是 SF4 内置类型（只是不可编辑），非 slimefun 的才是 addon 喵
            boolean isSf4 = key.startsWith("slimefun:");
            map.put(key, new RecipeTypeInfo(key, name, slots, guessCols(slots), false, isSf4, false));
        }

        return map;
    }

    private String buildRecipesJson() {
        Map<String, RecipeType> resolved = collectRuntimeRecipeTypes(resolveBuiltinTypes());
        Map<String, String> names = new LinkedHashMap<>();

        StringBuilder sb = new StringBuilder("{\"items\":{");
        boolean firstItem = true;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            String id = item.getId();
            ItemStack[] recipe = item.getRecipe();
            if (recipe == null || recipe.length == 0) continue;
            names.put(id, item.getItemName());

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
                String stackId = itemIdFromStack(recipe[i]);
                names.put(stackId, displayNameFromStack(recipe[i], stackId));
                sb.append('"').append(escapeJson(stackId)).append('"');
            }
            sb.append("],");

            // addonRecipes：只输出不可编辑类型的只读配方；可编辑类型由前端从currentRecipe预填充喵
            sb.append("\"addonRecipes\":[");
            RecipeSnapshot snap = originalRecipes.get(id);
            List<String> addonJsonParts = new ArrayList<>();
            if (snap != null && snap.type != null && !isNullRecipeType(snap.type.getKey().toString())) {
                String snapRtKey = snap.type.getKey().toString();
                if (!EDITABLE_RECIPE_TYPES.contains(snapRtKey)) {
                    ItemStack[] snapRecipe = snap.recipe != null ? snap.recipe : new ItemStack[0];
                    for (ItemStack stack : snapRecipe) {
                        String stackId = itemIdFromStack(stack);
                        names.put(stackId, displayNameFromStack(stack, stackId));
                    }
                    addonJsonParts.add(defaultRecipeJson(id, snapRtKey, snapRecipe));
                }
                for (RecipeEntry entry : snap.additionalRecipes) {
                    String entryRtKey = entry.getRecipeType().getKey().toString();
                    if (EDITABLE_RECIPE_TYPES.contains(entryRtKey)) continue;
                    ItemStack[] entryRecipe = entry.getRecipe();
                    for (ItemStack stack : entryRecipe) {
                        String stackId = itemIdFromStack(stack);
                        names.put(stackId, displayNameFromStack(stack, stackId));
                    }
                    addonJsonParts.add(defaultRecipeJson(id, entryRtKey, entryRecipe));
                }
            }
            sb.append(String.join(",", addonJsonParts));
            sb.append("],");

            // recipes：SCG 存储的可编辑配方（追加在 addon 配方之上）喵
            sb.append("\"recipes\":[");
            boolean hasStored = false;
            if (storedRecipes != null) {
                StoredRecipesSection parsed = parseRecipes(storedRecipes, id);
                List<Map<?, ?>> recipes = parsed.recipes;
                if (recipes != null) {
                    for (Map<?, ?> r : recipes) {
                        if (isNullRecipeType(String.valueOf(r.get("type")))) continue;
                        if (hasStored) sb.append(',');
                        hasStored = true;
                        collectRecipeNames(names, r, id);
                        sb.append(recipeToJson(r, id));
                    }
                }
            }
            sb.append(']');
            sb.append('}');
        }

        sb.append("},\"names\":{");
        boolean firstName = true;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            if (!firstName) sb.append(','); firstName = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":\"").append(escapeJson(entry.getValue())).append('"');
        }
        sb.append("},\"recipeTypes\":[");
        Map<String, RecipeTypeInfo> allTypes = collectAllRecipeTypes(resolved);
        boolean firstType = true;
        for (RecipeTypeInfo info : allTypes.values()) {
            if (!firstType) sb.append(','); firstType = false;
            sb.append("{\"key\":\"").append(escapeJson(info.key)).append("\",")
              .append("\"name\":\"").append(escapeJson(info.name)).append("\",")
              .append("\"slots\":").append(info.slots).append(",")
              .append("\"cols\":").append(info.cols).append(",")
              .append("\"hasTime\":").append(info.hasTime).append(",")
              .append("\"isBuiltin\":").append(info.isBuiltin).append(",")
              .append("\"isEditable\":").append(info.isEditable)
              .append("}");
        }
        sb.append("]}");

        return sb.toString();
    }

    private static void collectRecipeNames(Map<String, String> names, Map<?, ?> recipe, String fallbackId) {
        Object input = recipe.get("input");
        if (input instanceof List) {
            for (Object value : (List<?>) input) {
                String id = String.valueOf(value);
                names.put(id, displayNameForId(id));
            }
        }
        Object output = recipe.get("output");
        String outputId = output != null ? String.valueOf(output) : fallbackId;
        names.put(outputId, displayNameForId(outputId));
    }

    private static String displayNameFromStack(ItemStack stack, String fallbackId) {
        if (stack == null || stack.getType() == Material.AIR) return "空";
        SlimefunItem sfItem = SlimefunItem.getByItem(stack);
        if (sfItem != null) return sfItem.getItemName();
        return displayNameForId(fallbackId);
    }

    private static String displayNameForId(String id) {
        if (id == null || id.isEmpty() || "AIR".equalsIgnoreCase(id)) return "空";
        Material mat = Material.matchMaterial(id);
        if (mat != null) return VanillaMaterialLocalization.getItemName(mat);
        SlimefunItem sfItem = IconParser.findSlimefunItem(id);
        if (sfItem != null) return sfItem.getItemName();
        return id;
    }

    private String defaultRecipeJson(String itemId, String rtKey, ItemStack[] recipe) {
        int slots = guessSlots(rtKey);
        List<String> inputIds = new ArrayList<>();
        for (int i = 0; i < Math.min(recipe.length, slots); i++) {
            inputIds.add(itemIdFromStack(recipe[i]));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(escapeJson(rtKey)).append("\",")
          .append("\"input\":[").append(jsonStringList(inputIds)).append("],")
          .append("\"output\":\"").append(escapeJson(itemId)).append("\",")
          .append("\"outputAmount\":1,\"processingTime\":0}");
        return sb.toString();
    }

    private static String recipeToJson(Map<?, ?> r, String fallbackId) {
        String type = String.valueOf(r.get("type"));
        Object rawInput = r.get("input");
        List<?> inputList = rawInput instanceof List ? (List<?>) rawInput : Collections.emptyList();
        String output = r.get("output") != null ? String.valueOf(r.get("output")) : fallbackId;
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

    private static Map<String, RecipeType> resolveBuiltinTypes() {
        Map<String, RecipeType> map = new LinkedHashMap<>();
        for (Field f : RecipeType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
            if (!RecipeType.class.isAssignableFrom(f.getType())) continue;
            try {
                RecipeType rt = (RecipeType) f.get(null);
                String k = rt.getKey().toString();
                if (EDITABLE_RECIPE_TYPES.contains(k)) map.put(k, rt);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static class RecipeTypeInfo {
        final String key, name;
        final int slots, cols;
        final boolean hasTime, isBuiltin, isEditable;
        RecipeTypeInfo(String k, String n, int s, int c, boolean t, boolean b, boolean e) {
            key = k; name = n; slots = s; cols = c; hasTime = t; isBuiltin = b; isEditable = e;
        }
    }

    private static class StoredRecipesSection {
        final List<Map<?, ?>> recipes;
        StoredRecipesSection(List<Map<?, ?>> r) { recipes = r; }
    }

    private static StoredRecipesSection parseRecipes(YamlConfiguration yaml, String itemId) {
        List<?> raw = yaml.getList("slimefun." + itemId + ".recipes");
        if (raw == null) return new StoredRecipesSection(null);
        List<Map<?, ?>> list = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Map) list.add((Map<?, ?>) entry);
        }
        return new StoredRecipesSection(list);
    }

    private boolean saveRecipesFromJson(String json) {
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

        Map<String, List<Map<String, Object>>> parsed;
        try {
            parsed = parseRecipeSavePayload(json);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid recipe save payload", e);
            return false;
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : parsed.entrySet()) {
            root.set(entry.getKey(), null);
            if (entry.getValue().isEmpty()) continue;
            ConfigurationSection itemSec = root.createSection(entry.getKey());
            itemSec.set("recipes", entry.getValue());
        }

        File tempFile = new File(plugin.getDataFolder(), "Recipes.yml.tmp." + System.currentTimeMillis());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            writer.write(yaml.saveToString());
            writer.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save Recipes.yml", e);
            return false;
        }
        YamlConfiguration previousRecipes = storedRecipes;
        String previousContent = null;
        if (finalFile.exists()) {
            try {
                previousContent = new String(java.nio.file.Files.readAllBytes(finalFile.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) { plugin.getLogger().log(Level.WARNING, "Failed to read Recipes.yml for rollback backup", e); }
        }

        try {
            java.nio.file.Files.move(tempFile.toPath(), finalFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                java.nio.file.Files.move(tempFile.toPath(), finalFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                plugin.getLogger().log(Level.WARNING, "Failed to replace Recipes.yml", e2);
                return false;
            }
        }

        storedRecipes = yaml;
        try {
            runSync(RecipeApiHandler::applyAllRecipes);
        } catch (Exception e) {
            storedRecipes = previousRecipes;
            restoreRecipesFile(finalFile, previousContent);
            try { runSync(RecipeApiHandler::applyAllRecipes); } catch (Exception ignored) {}
            plugin.getLogger().log(Level.WARNING, "Failed to apply Recipes.yml", e);
            return false;
        }

        plugin.getLogger().info("Recipes.yml saved and applied");
        return true;
    }

    private void restoreRecipesFile(File finalFile, String previousContent) {
        try {
            if (previousContent == null) {
                java.nio.file.Files.deleteIfExists(finalFile.toPath());
            } else {
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(finalFile), StandardCharsets.UTF_8)) {
                    writer.write(previousContent);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore Recipes.yml after apply failure", e);
        }
    }

    private static void runSync(Runnable task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            task.run();
            return null;
        }).get();
    }

    static Map<String, List<Map<String, Object>>> parseRecipeSavePayload(String json) {
        Map<String, List<Map<String, Object>>> parsedRecipes = new LinkedHashMap<>();
        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject()) return parsedRecipes;
        JsonObject rootObj = parsed.getAsJsonObject();
        if (!rootObj.has("items") || !rootObj.get("items").isJsonObject()) return parsedRecipes;
        JsonObject itemsObj = rootObj.getAsJsonObject("items");
        for (Map.Entry<String, JsonElement> itemEntry : itemsObj.entrySet()) {
            if (!itemEntry.getValue().isJsonObject()) continue;
            JsonObject itemObj = itemEntry.getValue().getAsJsonObject();
            if (!itemObj.has("recipes") || !itemObj.get("recipes").isJsonArray()) continue;
            List<Map<String, Object>> recipeList = new ArrayList<>();
            for (JsonElement recipeElement : itemObj.getAsJsonArray("recipes")) {
                if (!recipeElement.isJsonObject()) continue;
                JsonObject recipeObj = recipeElement.getAsJsonObject();
                String type = jsonString(recipeObj, "type");
                if (isNullRecipeType(type)) continue;
                Map<String, Object> map = new LinkedHashMap<>();
                if (type != null) map.put("type", type);
                JsonArray inputArray = jsonArray(recipeObj, "input");
                if (inputArray != null) {
                    List<String> input = new ArrayList<>();
                    for (JsonElement inputElement : inputArray) {
                        if (inputElement.isJsonPrimitive()) input.add(inputElement.getAsString());
                    }
                    map.put("input", input);
                }
                String output = jsonString(recipeObj, "output");
                if (output != null) map.put("output", output);
                map.put("output-amount", clamp(jsonInt(recipeObj, "outputAmount", 1), 1, 64));
                int processingTime = jsonInt(recipeObj, "processingTime", 0);
                if (processingTime > 0) map.put("processing-time", processingTime);
                recipeList.add(map);
            }
            parsedRecipes.put(itemEntry.getKey(), recipeList);
        }
        return parsedRecipes;
    }

    private static String jsonString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static JsonArray jsonArray(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
    }

    private static int jsonInt(JsonObject obj, String key, int fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return fallback;
        try { return obj.get(key).getAsInt(); } catch (Exception e) { return fallback; }
    }

    private static void applyAllRecipes() {
        restoreOriginalRecipes();
        if (storedRecipes == null) return;

        ConfigurationSection root = storedRecipes.getConfigurationSection("slimefun");
        if (root == null) return;

        Map<String, RecipeType> resolved = collectRuntimeRecipeTypes(resolveBuiltinTypes());

        for (String itemId : root.getKeys(false)) {
            SlimefunItem item = IconParser.findSlimefunItem(itemId);
            if (item == null) continue;

            StoredRecipesSection parsed = parseRecipes(storedRecipes, itemId);
            List<Map<?, ?>> recipes = parsed.recipes;
            if (recipes == null || recipes.isEmpty()) continue;

            // 喵~判断 addon 是否已有配方：原始快照的 RecipeType 不为 null 且不为 :null 结尾则视为有 addon 配方
            RecipeSnapshot snapshot = originalRecipes.get(itemId);
            boolean hasAddonRecipe = snapshot != null
                    && snapshot.type != null
                    && !isNullRecipeType(snapshot.type.getKey().toString());

            boolean isFirstScgRecipe = true;
            for (Map<?, ?> recipeMap : recipes) {
                String typeKey = String.valueOf(recipeMap.get("type"));
                if (isNullRecipeType(typeKey)) continue;
                RecipeType rt = findRecipeTypeByKey(typeKey, resolved);
                if (rt == null) continue;

                Object inputObj = recipeMap.get("input");
                if (!(inputObj instanceof List)) continue;
                List<?> inputList = (List<?>) inputObj;
                Object outputVal = recipeMap.get("output");
                int outputAmount = clamp(toInt(recipeMap.get("output-amount"), 1), 1, 64);

                int expectedSlots = guessSlots(typeKey);
                ItemStack[] inputStacks = new ItemStack[expectedSlots];
                for (int i = 0; i < Math.min(inputList.size(), expectedSlots); i++) {
                    String matId = String.valueOf(inputList.get(i));
                    inputStacks[i] = resolveItemStack(matId);
                }

                String outId = outputVal != null ? String.valueOf(outputVal) : itemId;
                ItemStack outputStack = resolveItemStack(outId);
                if (outputStack == null) outputStack = new ItemStack(Material.AIR);
                outputStack.setAmount(outputAmount);

                // 喵~无 addon 配方时，第一条 SCG 配方用 setRecipe 全量注册，后续用 addRecipe
                // 有 addon 配方时，始终用 addRecipe 追加，不覆盖 addon 主配方
                ItemStack[] padded = new ItemStack[9];
                System.arraycopy(inputStacks, 0, padded, 0, Math.min(inputStacks.length, 9));

                if (!hasAddonRecipe && isFirstScgRecipe) {
                    item.setRecipeType(rt);
                    item.setRecipe(padded);
                    item.setRecipeOutput(outputStack);
                    isFirstScgRecipe = false;

                    int processingTime = toInt(recipeMap.get("processing-time"), 0);
                    if (processingTime > 0 && TIMED_RECIPE_TYPES.contains(typeKey)) {
                        try {
                            Method setProcessingTime = item.getClass().getMethod("setProcessingTime", int.class);
                            setProcessingTime.invoke(item, processingTime);
                        } catch (NoSuchMethodException ignored) {
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to set processing time for " + itemId, e);
                        }
                    }
                } else {
                    item.addRecipe(rt, padded, outputStack);
                    isFirstScgRecipe = false;
                }

                rt.register(inputStacks, outputStack);
            }
        }

        plugin.getLogger().info("Recipes applied in real-time");
    }

    private static void captureOriginalRecipes() {
        if (!originalRecipes.isEmpty()) return;
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            originalRecipes.put(item.getId(), new RecipeSnapshot(item));
        }
    }

    private static void restoreOriginalRecipes() {
        for (Map.Entry<String, RecipeSnapshot> entry : originalRecipes.entrySet()) {
            SlimefunItem item = IconParser.findSlimefunItem(entry.getKey());
            if (item != null) entry.getValue().restore(item);
        }
        rebuildMachineRecipesFromOriginals();
    }

    private static void rebuildMachineRecipesFromOriginals() {
        Map<MultiBlockMachine, List<ItemStack[]>> toRebuild = new LinkedHashMap<>();

        // 喵~所有物品都从原始快照重建 MultiBlockMachine 配方列表，不跳过 storedRecipes 里的物品
        // 原来跳过 storedRecipes 物品的逻辑会导致 addon 主配方在 clearRecipe 后丢失喵
        for (Map.Entry<String, RecipeSnapshot> entry : originalRecipes.entrySet()) {
            SlimefunItem item = IconParser.findSlimefunItem(entry.getKey());
            if (item == null || item.getRecipe() == null) continue;
            String machineId = getRecipeTypeMachineId(entry.getValue().type);
            if (machineId == null) continue;
            SlimefunItem mItem = SlimefunItem.getById(machineId);
            if (mItem instanceof MultiBlockMachine) {
                MultiBlockMachine mbm = (MultiBlockMachine) mItem;
                ItemStack output = item.getRecipeOutput();
                if (output == null) continue;
                toRebuild.computeIfAbsent(mbm, k -> new ArrayList<>()).add(item.getRecipe());
                toRebuild.computeIfAbsent(mbm, k -> new ArrayList<>()).add(new ItemStack[]{output});
            }
        }
        for (MultiBlockMachine mbm : toRebuild.keySet()) {
            mbm.clearRecipe();
        }
        for (Map.Entry<MultiBlockMachine, List<ItemStack[]>> entry : toRebuild.entrySet()) {
            List<ItemStack[]> recs = entry.getValue();
            for (int i = 0; i < recs.size(); i += 2) {
                entry.getKey().addRecipe(recs.get(i), recs.get(i + 1)[0]);
            }
        }
    }

    private static Field recipeTypeMachineField;

    private static String getRecipeTypeMachineId(RecipeType rt) {
        if (recipeTypeMachineField == null) {
            try {
                recipeTypeMachineField = RecipeType.class.getDeclaredField("machine");
                recipeTypeMachineField.setAccessible(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to access RecipeType.machine field", e);
                return null;
            }
        }
        try { return (String) recipeTypeMachineField.get(rt); }
        catch (Exception e) { return null; }
    }

    private static class RecipeSnapshot {
        private final RecipeType type;
        private final ItemStack[] recipe;
        private final ItemStack output;
        private final int processingTime;
        private final List<RecipeEntry> additionalRecipes;

        RecipeSnapshot(SlimefunItem item) {
            this.type = item.getRecipeType();
            ItemStack[] sourceRecipe = item.getRecipe();
            this.recipe = sourceRecipe == null ? null : Arrays.stream(sourceRecipe).map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
            ItemStack sourceOutput = item.getRecipeOutput();
            this.output = sourceOutput == null ? null : sourceOutput.clone();
            int pt = 0;
            try { Method gpt = item.getClass().getMethod("getProcessingTime"); pt = (int) gpt.invoke(item); } catch (Exception ignored) {}
            this.processingTime = pt;
            this.additionalRecipes = new ArrayList<>(item.getAdditionalRecipes());
        }

        void restore(SlimefunItem item) {
            item.setRecipeType(type);
            if (recipe != null) {
                // 喵~防御：setRecipe强制length=9，restore时从快照恢复也必须保证是9位
                // 虽然快照来源于item.getRecipe()理论上已是9位，但防御性pad防止脏数据导致崩溃
                ItemStack[] cloned = Arrays.stream(recipe)
                        .map(stack -> stack == null ? null : stack.clone())
                        .toArray(ItemStack[]::new);
                // 不足9位则pad到9位（补null），超出则截断
                if (cloned.length != 9) {
                    ItemStack[] padded = new ItemStack[9];
                    System.arraycopy(cloned, 0, padded, 0, Math.min(cloned.length, 9));
                    cloned = padded;
                }
                item.setRecipe(cloned);
            }
            if (output != null) {
                item.setRecipeOutput(output.clone());
            }
            if (processingTime > 0) {
                try {
                    Method setProcessingTime = item.getClass().getMethod("setProcessingTime", int.class);
                    setProcessingTime.invoke(item, processingTime);
                } catch (Exception ignored) {}
            }
            // Clear current additional recipes and restore originals
            item.clearAdditionalRecipes();
            for (RecipeEntry entry : additionalRecipes) {
                item.addRecipe(entry.getRecipeType(), entry.getRecipe(), entry.getRecipeOutput());
            }
        }
    }

    private static ItemStack resolveItemStack(String id) {
        if (id == null || id.isEmpty() || "AIR".equalsIgnoreCase(id)) return null;
        Material mat = Material.matchMaterial(id);
        if (mat != null) return new ItemStack(mat);
        SlimefunItem sfItem = IconParser.findSlimefunItem(id);
        if (sfItem != null && sfItem.getRecipeOutput() != null) { ItemStack s = sfItem.getRecipeOutput().clone(); s.setAmount(1); return s; }
        plugin.getLogger().warning("Unknown recipe item ID, using AIR: " + id);
        return new ItemStack(Material.AIR);
    }

    private String itemIdFromStack(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return "AIR";
        // 喵~优先读 PDC（SlimefunItemStack 或已写入 PDC 的物品）喵
        SlimefunItem sfItem = SlimefunItem.getByItem(stack);
        if (sfItem != null) return sfItem.getId();
        // 喵~PDC 未命中时，遍历注册表用 isItemSimilar 逐一比对喵
        // 覆盖 addon 物品在快照时 PDC 未写入的场景（clone 丢失 SlimefunItemStack 类型）喵
        for (SlimefunItem candidate : io.github.thebusybiscuit.slimefun4.implementation.Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils.isItemSimilar(stack, candidate.getItem(), false, false)) {
                return candidate.getId();
            }
        }
        return stack.getType().name();
    }

    private static RecipeType findRecipeTypeByKey(String key, Map<String, RecipeType> resolved) {
        if (isNullRecipeType(key)) return null;
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

    private static Map<String, RecipeType> collectRuntimeRecipeTypes(Map<String, RecipeType> base) {
        Map<String, RecipeType> map = new LinkedHashMap<>(base);
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            RecipeType rt = item.getRecipeType();
            if (rt == null || rt.getKey() == null) continue;
            map.putIfAbsent(rt.getKey().toString(), rt);
        }
        return map;
    }

    private static boolean isNullRecipeType(String key) {
        return key == null || "null".equalsIgnoreCase(key) || key.toLowerCase(Locale.ROOT).endsWith(":null");
    }

    private String readDisplayName(RecipeType rt, String key) {
        // \u55B5~\u4F18\u5148\u7528\u5185\u7F6E\u4E2D\u6587\u540D\u6620\u5C04\uFF0C\u907F\u514D\u8BFB\u5230\u82F1\u6587 lore\uFF08\u5982 "A regular Crafting Table cannot..."\uFF09\u55B5
        String shortKey = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
        if ("null".equals(shortKey)) return "\u65E0\u7279\u5B9A\u5408\u6210\u65B9\u5F0F";
        String chinese = builtinRecipeTypeName(shortKey);
        if (chinese != null) return chinese;
        // \u6CA1\u6709\u4E2D\u6587\u6620\u5C04\u65F6\u518D\u5C1D\u8BD5\u8BFB lore\uFF08addon \u81EA\u5B9A\u4E49\u7C7B\u578B\uFF09\u55B5
        try {
            ItemStack rtItem = rt != null ? rt.toItem() : null;
            if (rtItem != null && rtItem.hasItemMeta() && rtItem.getItemMeta().hasLore()) {
                for (String lore : rtItem.getItemMeta().getLore()) {
                    if (lore != null && !lore.trim().isEmpty())
                        return ColorUtil.stripColorCodes(lore.trim());
                }
            }
        } catch (Exception ignored) {}
        return shortKey.replace('_', ' ');
    }

    private static String builtinRecipeTypeName(String shortKey) {
        switch (shortKey) {
            case "enhanced_crafting_table": return "增强型工作台";
            case "armor_forge": return "盔甲锻造台";
            case "grind_stone": return "磨石";
            case "smeltery": return "冶炼炉";
            case "ore_crusher": return "矿石粉碎机";
            case "compressor": return "压缩机";
            case "pressure_chamber": return "压力舱";
            case "magic_workbench": return "魔法工作台";
            case "gold_pan": return "淘金盘";
            case "juicer": return "榨汁机";
            case "ancient_altar": return "远古祭坛";
            case "heated_pressure_chamber": return "加热压力舱";
            case "ore_washer": return "洗矿机";
            case "table_saw": return "锯木机";
            case "freezer": return "冷冻机";
            case "food_fabricator": return "食品加工机";
            case "food_composter": return "食品堆肥机";
            case "reactor": return "反应堆";
            case "refinery": return "精炼机";
            case "automated_panning_machine": return "自动淘金机";
            case "miner_android": return "矿工机器人";
            case "fisherman_android": return "渔夫机器人";
            case "geo_miner": return "地质矿机";
            case "oil_pump": return "石油泵";
            case "nuclear_reactor": return "核反应堆";
            case "shaped": return "有序合成";
            case "shapeless": return "无序合成";
            default: return null;
        }
    }

    private static int guessSlots(String key) {
        if (ADDON_RECIPE_TYPE_SLOTS.containsKey(key)) {
            return ADDON_RECIPE_TYPE_SLOTS.get(key);
        }
        String shortKey = key.contains(":") ? key.substring(key.lastIndexOf(':') + 1) : key;
        switch (shortKey) {
            case "enhanced_crafting_table": case "armor_forge": case "magic_workbench":
            case "ancient_altar": case "shaped": case "shapeless": case "null": return 9;
            case "smeltery": case "heated_pressure_chamber": case "ore_crusher":
            case "compressor": case "grind_stone": case "juicer": case "gold_pan":
            case "freezer": case "food_fabricator": case "food_composter":
            case "reactor": case "refinery": case "pressure_chamber": case "table_saw":
            case "ore_washer": case "oil_pump": case "geo_miner":
            case "miner_android": case "fisherman_android":
            case "nuclear_reactor": case "automated_panning_machine":
                return 1;
            default: return 1;
        }
    }

    private static int guessCols(int slots) {
        if (slots == 9) return 3;
        if (slots == 2) return 2;
        return 1;
    }

    private static int toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) try { return Integer.parseInt((String) v); } catch (NumberFormatException e) {}
        return def;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
