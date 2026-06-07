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
package cn.rmc.slimefunweaver.config;

import cn.rmc.slimefunweaver.model.*;
import cn.rmc.slimefunweaver.util.IconParser;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CategoryConfigLoader {

    private static final int MAX_DEPTH = 30;

    private CategoryConfigLoader() {}

    public static List<CustomCategory> load(File categoriesFile, Logger logger) {
        if (!categoriesFile.exists()) {
            logger.warning("categories.yml not found, custom guide will be empty");
            return Collections.emptyList();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(categoriesFile);
        ConfigurationSection section = yaml.getConfigurationSection("categories");
        if (section == null) {
            logger.warning("No 'categories' root in categories.yml");
            return Collections.emptyList();
        }

        List<CustomCategory> roots = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub != null) {
                CustomCategory cat = parseCategory(key, sub, logger, 0);
                if (cat != null) roots.add(cat);
            }
        }
        calculateStats(roots);
        return roots;
    }

    private static CustomCategory parseCategory(String key, ConfigurationSection section, Logger logger, int depth) {
        if (depth > MAX_DEPTH) {
            logger.log(Level.WARNING, "Category [{0}] exceeds max depth of {1}, skipped", new Object[]{key, MAX_DEPTH});
            return null;
        }
        String display = section.getString("display");
        IconSource icon = parseIconSource(section, logger, key);
        if (icon == null) {
            logger.log(Level.WARNING, "Category [{0}] missing icon, using BOOK", key);
            icon = new IconSource(IconType.VANILLA, "BOOK");
        }

        List<String> lore = section.getStringList("lore");
        int page = Math.max(1, section.getInt("page", 1));
        int slot = section.getInt("slot", -1);
        boolean glow = section.getBoolean("glow", false);

        CustomCategory category = new CustomCategory(key, display, icon, lore, page, slot, glow);

        parseItems(category, section, logger);

        for (String subKey : section.getKeys(false)) {
            if (isReservedKey(subKey)) continue;
            ConfigurationSection subSection = section.getConfigurationSection(subKey);
            if (subSection != null) {
                CustomCategory sub = parseCategory(subKey, subSection, logger, depth + 1);
                if (sub != null) category.addChild(sub);
            }
        }

        return category;
    }

    private static boolean isReservedKey(String key) {
        return key.equals("display") || key.equals("icon") || key.equals("lore")
            || key.equals("page") || key.equals("slot") || key.equals("items")
            || key.equals("glow") || key.equals("ref") || key.equals("mode");
    }

    private static IconSource parseIconSource(ConfigurationSection section, Logger logger, String context) {
        ConfigurationSection iconSection = section.getConfigurationSection("icon");
        if (iconSection == null) return null;

        String typeStr = iconSection.getString("type");
        String id = iconSection.getString("id");
        if (typeStr == null || id == null) {
            logger.log(Level.WARNING, "Category [{0}] icon type or id missing", context);
            return null;
        }
        try {
            return new IconSource(IconType.valueOf(typeStr.toUpperCase(Locale.ENGLISH)), id);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Category [{0}] invalid icon type: {1}", new Object[]{context, typeStr});
            return null;
        }
    }

    private static void parseItems(CustomCategory parent, ConfigurationSection section, Logger logger) {
        List<Map<?, ?>> rawItems = section.getMapList("items");
        if (rawItems == null || rawItems.isEmpty()) return;

        for (Map<?, ?> rawItem : rawItems) {
            Object idObj = rawItem.get("id");
            Object placeholderObj = rawItem.get("placeholder");

            if (idObj != null) {
                String slimefunId = idObj.toString();
                SlimefunItem sfItem = IconParser.findSlimefunItem(slimefunId);
                if (sfItem == null) {
                    logger.log(Level.WARNING, "Item [{0}] not registered, skipped", slimefunId);
                    continue;
                }

                int page = Math.max(1, getInt(rawItem, "page", 1));
                int slot = getInt(rawItem, "slot", -1);
                if (slot < 0 || slot >= 36) {
                    logger.log(Level.WARNING, "Item [{0}] has invalid slot {1}, skipped", new Object[]{slimefunId, slot});
                    continue;
                }
                parent.addChild(new CustomItemEntry(slimefunId, sfItem, page, slot));

            } else if (placeholderObj instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) placeholderObj;
                Object refObj = data.get("ref");

                if (refObj != null) {
                    String targetKey = refObj.toString();
                    String mode = (String) data.get("mode");
                    if (mode == null || mode.isEmpty()) mode = "custom";
                    String refDisplay = (String) data.get("display");

                    Object iconObj = data.get("icon");
                    IconSource refIcon = null;
                    if (iconObj instanceof Map) {
                        Map<?, ?> iconData = (Map<?, ?>) iconObj;
                        String typeStr = (String) iconData.get("type");
                        String iconId = (String) iconData.get("id");
                        if (typeStr != null && iconId != null) {
                            try { refIcon = new IconSource(IconType.valueOf(typeStr.toUpperCase(Locale.ENGLISH)), iconId); }
                            catch (IllegalArgumentException e) { logger.log(Level.WARNING, "Reference icon type invalid: {0}", typeStr); }
                        }
                    }
                    if (refIcon == null) refIcon = new IconSource(IconType.VANILLA, "ARROW");

                    List<String> refLore = new ArrayList<>();
                    Object loreObj = data.get("lore");
                    if (loreObj instanceof List) for (Object line : (List<?>) loreObj) if (line != null) refLore.add(line.toString());

                    int refPage = Math.max(1, getInt(data, "page", 1));
                    int refSlot = getInt(data, "slot", -1);
                    if (refSlot < 0 || refSlot >= 36) {
                        logger.log(Level.WARNING, "Reference [{0}] has invalid slot {1}, skipped", new Object[]{targetKey, refSlot});
                        continue;
                    }
                    boolean refGlow = getBoolean(data, "glow", false);
                    parent.addChild(new CustomReferenceEntry(targetKey, mode, refDisplay, refLore, refPage, refSlot, refGlow, refIcon));
                    continue;
                }

                Object iconObj = data.get("icon");
                if (!(iconObj instanceof Map)) {
                    logger.warning("Placeholder icon is not a map, skipped");
                    continue;
                }
                Map<?, ?> iconData = (Map<?, ?>) iconObj;
                if (iconData == null) {
                    logger.warning("Placeholder missing icon, skipped");
                    continue;
                }

                String typeStr = (String) iconData.get("type");
                String iconId = (String) iconData.get("id");
                if (typeStr == null || iconId == null) {
                    logger.warning("Placeholder icon incomplete, skipped");
                    continue;
                }

                IconType iconType;
                try { iconType = IconType.valueOf(typeStr.toUpperCase(Locale.ENGLISH)); }
                catch (IllegalArgumentException e) {
                    logger.log(Level.WARNING, "Placeholder icon type invalid: {0}", typeStr);
                    continue;
                }

                String display = (String) data.get("display");

                List<String> lore = new ArrayList<>();
                Object loreObj = data.get("lore");
                if (loreObj instanceof List) {
                    for (Object line : (List<?>) loreObj) {
                        if (line != null) lore.add(line.toString());
                    }
                }

                int page = Math.max(1, getInt(data, "page", 1));
                int slot = getInt(data, "slot", -1);
                if (slot < 0 || slot >= 36) {
                    logger.log(Level.WARNING, "Placeholder has invalid slot {0}, skipped", slot);
                    continue;
                }
                boolean glow = getBoolean(data, "glow", false);
                parent.addChild(new CustomPlaceholderEntry(
                        new IconSource(iconType, iconId), display, lore, page, slot, glow));
            }
        }
    }

    private static int getInt(Map<?, ?> map, String key, int defaultValue) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : defaultValue;
    }

    private static boolean getBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        return val instanceof Boolean ? ((Boolean) val) : defaultValue;
    }

    private static void calculateStats(List<CustomCategory> roots) {
        for (CustomCategory root : roots) calculateStatsRecursive(root);
    }

    private static void calculateStatsRecursive(CustomCategory cat) {
        int childCats = 0, directItems = 0, totalItems = 0;
        for (GuideTreeNode child : cat.getChildren()) {
            if (child instanceof CustomCategory) {
                childCats++;
                calculateStatsRecursive((CustomCategory) child);
                totalItems += ((CustomCategory) child).getTotalItemsCount();
            } else if (child instanceof CustomReferenceEntry) {
                childCats++;
            } else if (child instanceof CustomItemEntry) {
                directItems++;
                totalItems++;
            }
        }
        cat.setChildrenCount(childCats);
        cat.setDirectItemsCount(directItems);
        cat.setTotalItemsCount(totalItems);
    }
}
