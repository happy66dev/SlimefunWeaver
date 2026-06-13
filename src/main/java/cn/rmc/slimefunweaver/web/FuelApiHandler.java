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
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.AbstractEnergyProvider;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class FuelApiHandler implements HttpHandler {

    private final SlimefunWeaver plugin;

    public FuelApiHandler(SlimefunWeaver plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/fuel-types")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                handleFuelTypes(exchange, method);
            } else if (path.equals("/api/fuels")) {
                if (!WebSecurity.isAccessAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
                handleFuels(exchange, method);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Fuel API error", e);
            try { exchange.sendResponseHeaders(500, -1); } catch (IOException ignored) {}
        }
    }

    private void handleFuelTypes(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        serveJson(exchange, buildFuelTypesJson());
    }

    private void handleFuels(HttpExchange exchange, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            serveJson(exchange, buildFuelTypesJson());
        } else if ("PUT".equalsIgnoreCase(method)) {
            if (!WebSecurity.isWriteAllowed(plugin, exchange)) { exchange.sendResponseHeaders(403, -1); return; }
            String body;
            try { body = WebSecurity.readBody(exchange); }
            catch (WebSecurity.BodyTooLargeException e) { exchange.sendResponseHeaders(413, -1); return; }
            if (body == null || body.isEmpty()) { exchange.sendResponseHeaders(400, -1); return; }
            if (!saveFuelsFromJson(body)) { exchange.sendResponseHeaders(500, -1); return; }
            serveJson(exchange, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private String buildFuelTypesJson() {
        StringBuilder sb = new StringBuilder("{\"generators\":[");
        boolean first = true;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (!(item instanceof AbstractEnergyProvider)) continue;
            AbstractEnergyProvider generator = (AbstractEnergyProvider) item;

            if (!first) sb.append(',');
            first = false;

            sb.append("{\"id\":\"").append(escapeJson(item.getId())).append("\",");
            sb.append("\"name\":\"").append(escapeJson(item.getItemName())).append("\",");

            int energyPerTick = 0;
            try {
                Method getEnergyProduction = AbstractEnergyProvider.class.getDeclaredMethod("getEnergyProduction");
                getEnergyProduction.setAccessible(true);
                energyPerTick = (int) getEnergyProduction.invoke(generator);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get energy production for " + item.getId(), e);
            }
            sb.append("\"energyPerTick\":").append(energyPerTick).append(",");

            sb.append("\"fuels\":[");
            boolean firstFuel = true;
            for (MachineFuel fuel : generator.getFuelTypes()) {
                if (!firstFuel) sb.append(',');
                firstFuel = false;

                ItemStack input = fuel.getInput();
                String inputId = itemIdFromStack(input);
                String inputName = displayNameFromStack(input, inputId);
                int burnTime = fuel.getTicks() / 2;

                ItemStack output = fuel.getOutput();
                String outputId = output != null ? itemIdFromStack(output) : null;
                String outputName = output != null ? displayNameFromStack(output, outputId) : null;

                sb.append("{\"inputId\":\"").append(escapeJson(inputId)).append("\",");
                sb.append("\"inputName\":\"").append(escapeJson(inputName)).append("\",");
                sb.append("\"burnTime\":").append(burnTime).append(",");
                sb.append("\"outputId\":").append(outputId != null ? "\"" + escapeJson(outputId) + "\"" : "null").append(",");
                sb.append("\"outputName\":").append(outputName != null ? "\"" + escapeJson(outputName) + "\"" : "null").append("}");
            }
            sb.append("]}");
        }

        sb.append("]}");
        return sb.toString();
    }

    private boolean saveFuelsFromJson(String json) {
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) return false;
            JsonObject rootObj = parsed.getAsJsonObject();

            if (!rootObj.has("generatorId") || !rootObj.get("generatorId").isJsonPrimitive()) return false;
            String generatorId = rootObj.get("generatorId").getAsString();

            SlimefunItem sfItem = IconParser.findSlimefunItem(generatorId);
            if (sfItem == null) {
                plugin.getLogger().warning("Generator not found: " + generatorId);
                return false;
            }
            if (!(sfItem instanceof AbstractEnergyProvider)) {
                plugin.getLogger().warning("Item is not a generator: " + generatorId);
                return false;
            }
            AbstractEnergyProvider generator = (AbstractEnergyProvider) sfItem;

            if (!rootObj.has("fuels") || !rootObj.get("fuels").isJsonArray()) return false;
            JsonArray fuelsArray = rootObj.getAsJsonArray("fuels");

            try {
                runSync(() -> {
                    generator.getFuelTypes().clear();
                    for (JsonElement fuelElement : fuelsArray) {
                        if (!fuelElement.isJsonObject()) continue;
                        JsonObject fuelObj = fuelElement.getAsJsonObject();

                        if (!fuelObj.has("inputId") || !fuelObj.get("inputId").isJsonPrimitive()) continue;
                        String inputId = fuelObj.get("inputId").getAsString();
                        ItemStack input = resolveItemStack(inputId);
                        if (input == null) continue;

                        int burnTime = fuelObj.has("burnTime") && fuelObj.get("burnTime").isJsonPrimitive()
                                ? fuelObj.get("burnTime").getAsInt() : 16;

                        String outputId = fuelObj.has("outputId") && !fuelObj.get("outputId").isJsonNull()
                                ? fuelObj.get("outputId").getAsString() : null;
                        ItemStack output = outputId != null ? resolveItemStack(outputId) : null;

                        MachineFuel fuel = output != null ? new MachineFuel(burnTime, input, output) : new MachineFuel(burnTime, input);
                        generator.registerFuel(fuel);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply fuel changes for " + generatorId, e);
                return false;
            }

            plugin.getLogger().info("Fuels updated for generator: " + generatorId);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid fuel save payload", e);
            return false;
        }
    }

    private ItemStack resolveItemStack(String id) {
        if (id == null || id.isEmpty() || "AIR".equalsIgnoreCase(id)) return null;
        Material mat = Material.matchMaterial(id);
        if (mat != null) return new ItemStack(mat);
        SlimefunItem sfItem = IconParser.findSlimefunItem(id);
        if (sfItem != null && sfItem.getRecipeOutput() != null) {
            ItemStack s = sfItem.getRecipeOutput().clone();
            s.setAmount(1);
            return s;
        }
        plugin.getLogger().warning("Unknown fuel item ID, using AIR: " + id);
        return new ItemStack(Material.AIR);
    }

    private String itemIdFromStack(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return "AIR";
        SlimefunItem sfItem = SlimefunItem.getByItem(stack);
        if (sfItem != null) return sfItem.getId();
        return stack.getType().name();
    }

    private String displayNameFromStack(ItemStack stack, String fallbackId) {
        if (stack == null || stack.getType() == Material.AIR) return "空";
        SlimefunItem sfItem = SlimefunItem.getByItem(stack);
        if (sfItem != null) return sfItem.getItemName();
        return displayNameForId(fallbackId);
    }

    private String displayNameForId(String id) {
        if (id == null || id.isEmpty() || "AIR".equalsIgnoreCase(id)) return "空";
        Material mat = Material.matchMaterial(id);
        if (mat != null) return VanillaMaterialLocalization.getItemName(mat);
        SlimefunItem sfItem = IconParser.findSlimefunItem(id);
        if (sfItem != null) return sfItem.getItemName();
        return id;
    }

    private void serveJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static void runSync(Runnable task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().callSyncMethod(SlimefunWeaver.getInstance(), () -> {
            task.run();
            return null;
        }).get();
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