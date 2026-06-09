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

import cn.rmc.slimefunweaver.model.IconSource;
import cn.rmc.slimefunweaver.model.IconType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.inventory.meta.SkullMeta;

public final class IconParser {

    private IconParser() {}

    @Nullable
    public static SlimefunItem findSlimefunItem(String id) {
        if (id == null || id.isEmpty()) return null;
        SlimefunItem item = SlimefunItem.getById(id);
        if (item != null) return item;
        String lowId = id.toLowerCase(Locale.ENGLISH);
        NamespacedKey key = new NamespacedKey(Slimefun.instance(), lowId);
        item = SlimefunItem.getById(key.toString());
        if (item != null) return item;
        Map<String, SlimefunItem> map = Slimefun.getRegistry().getSlimefunItemIds();
        String keyStr = key.toString();
        for (Map.Entry<String, SlimefunItem> e : map.entrySet()) {
            String k = e.getKey();
            if (k.equalsIgnoreCase(keyStr) || k.substring(k.indexOf(':') + 1).equalsIgnoreCase(lowId)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static Optional<ItemStack> parse(IconSource source, Logger logger) {
        if (source == null) return Optional.empty();

        switch (source.getType()) {
            case VANILLA:  return parseVanilla(source.getId(), logger);
            case SLIMEFUN: return parseSlimefun(source.getId(), logger);
            case HEAD:     return parseHead(source.getId(), logger);
            default:       return Optional.empty();
        }
    }

    public static Optional<ItemStack> parse(IconSource source, Logger logger, boolean glow) {
        Optional<ItemStack> opt = parse(source, logger);
        if (glow && opt.isPresent()) {
            ItemStack item = opt.get();
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return opt;
    }

    private static Optional<ItemStack> parseVanilla(String id, Logger logger) {
        if (id == null || id.isEmpty()) {
            logger.warning("VANILLA icon ID is empty");
            return Optional.empty();
        }
        try {
            return Optional.of(new ItemStack(Material.valueOf(id.toUpperCase())));
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Material not found: {0}", id);
            return Optional.empty();
        }
    }

    private static Optional<ItemStack> parseSlimefun(String id, Logger logger) {
        if (id == null || id.isEmpty()) {
            logger.warning("SLIMEFUN icon ID is empty");
            return Optional.empty();
        }
        SlimefunItem sfItem = findSlimefunItem(id);
        if (sfItem != null) {
            ItemStack clone = sfItem.getItem().clone();
            ItemMeta meta = clone.getItemMeta();
            if (meta != null) {
                meta.setLore(null);
                meta.setDisplayName(null);
                clone.setItemMeta(meta);
            }
            return Optional.of(clone);
        }
        logger.log(Level.WARNING, "Slimefun item not found: {0}", id);
        return Optional.empty();
    }

    private static Optional<ItemStack> parseHead(String textureId, Logger logger) {
        if (textureId == null || textureId.isEmpty()) {
            logger.warning("HEAD texture is empty");
            return Optional.empty();
        }
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (textureId.length() <= 16) {
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwner(textureId);
                head.setItemMeta(meta);
            } else {
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                try {
                    UUID uuid = UUID.randomUUID();
                    Field profileField = meta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    Object profile = Class.forName("com.mojang.authlib.GameProfile")
                            .getConstructor(UUID.class, String.class)
                            .newInstance(uuid, "SCGTexture");
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    Object property = propertyClass.getConstructor(String.class, String.class)
                            .newInstance("textures", textureId);
                    Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
                    properties.getClass().getMethod("put", Object.class, Object.class)
                            .invoke(properties, "textures", property);
                    profileField.set(meta, profile);
                } catch (Exception e) {
                }
                head.setItemMeta(meta);
            }
            return Optional.of(head);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create head: " + textureId, e);
            return Optional.empty();
        }
    }
}
