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
package cn.rmc.slimefunweaver.util;

import cn.rmc.slimefunweaver.SlimefunWeaver;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;

public final class VanillaMaterialLocalization {

    private static final Map<String, String> ITEM_LOCALIZATIONS = new HashMap<>();
    private static final Map<String, String> MATERIAL_CACHE = new HashMap<>();
    private static boolean initialized = false;

    private VanillaMaterialLocalization() {}

    public static void initialize() {
        if (initialized) {
            return;
        }

        try (InputStream inputStream = VanillaMaterialLocalization.class.getResourceAsStream("/zh_cn.json")) {
            if (inputStream != null) {
                JsonObject jsonObject = new JsonParser().parse(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .getAsJsonObject();

                int count = 0;
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("item.minecraft.") || key.startsWith("block.minecraft.")) {
                        ITEM_LOCALIZATIONS.put(key, entry.getValue().getAsString());
                        count++;
                    }
                }

                SlimefunWeaver.getInstance().getLogger().info("Loaded " + count + " vanilla item localizations from zh_cn.json");
            } else {
                SlimefunWeaver.getInstance().getLogger().warning("Could not find zh_cn.json file");
            }
        } catch (Exception e) {
            SlimefunWeaver.getInstance().getLogger().log(Level.SEVERE, "Error loading zh_cn.json:", e);
        }

        initialized = true;
    }

    public static String getItemName(Material material) {
        if (!initialized) {
            initialize();
        }

        String materialName = material.name();
        String cached = MATERIAL_CACHE.get(materialName);
        if (cached != null) {
            return cached;
        }

        String materialKey = materialName.toLowerCase(java.util.Locale.ENGLISH);
        String key = "item.minecraft." + materialKey;
        String name = ITEM_LOCALIZATIONS.get(key);
        if (name == null) {
            key = "block.minecraft." + materialKey;
            name = ITEM_LOCALIZATIONS.get(key);
        }
        if (name == null) {
            name = fallbackName(materialName);
        }
        MATERIAL_CACHE.put(materialName, name);
        return name;
    }

    private static String fallbackName(String enumName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : enumName.replace('_', ' ').toLowerCase().toCharArray()) {
            if (c == ' ') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
