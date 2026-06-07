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
package cn.rmc.slimefuncustomguide.listener;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.guide.CustomGuideHistory;
import cn.rmc.slimefuncustomguide.guide.CustomGuideRenderer;
import cn.rmc.slimefuncustomguide.model.CustomCategory;
import cn.rmc.slimefuncustomguide.model.GuideTreeNode;
import cn.rmc.slimefuncustomguide.util.IconParser;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunGuideOpenEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomGuideListener implements Listener {

    public static final NamespacedKey MODE_KEY = new NamespacedKey("slimefuncustomguide", "custom_guide_mode");
    private static final NamespacedKey SAVE_KEY = new NamespacedKey("slimefuncustomguide", "scg_save");
    private static final int MAX_BREADCRUMB_LEN = 4000;
    private static final String MODE_CUSTOM = "custom";

    private final CustomGuidePlugin plugin;
    private final CustomGuideRenderer renderer;
    private final Map<Player, CustomGuideHistory> histories = new ConcurrentHashMap<>();

    public CustomGuideListener(CustomGuidePlugin plugin) {
        this.plugin = plugin;
        this.renderer = new CustomGuideRenderer(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGuideOpen(SlimefunGuideOpenEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isCustomGuideEnabled()) return;

        ItemStack guide = event.getGuide();

        if (!isCustomMode(guide)) {
            return;
        }

        event.setCancelled(true);
        handleGuideOpen(player, guide);
    }

    public void handleGuideOpen(Player player, ItemStack guide) {
        CustomGuidePlugin.debug(player, "handleGuideOpen: opening, tracking=" +
                plugin.getExternalViewActive().contains(player.getUniqueId()));

        plugin.getExternalViewActive().remove(player.getUniqueId());
        plugin.getScgCloseDedup().remove(player.getUniqueId());

        Runnable openTask = () -> {
            CustomGuideHistory history = histories.computeIfAbsent(player, p -> new CustomGuideHistory());
            history.clear();

            SlimefunGuideMode mode = SlimefunUtils.isItemSimilar(
                    guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false)
                    ? SlimefunGuideMode.CHEAT_MODE
                    : SlimefunGuideMode.SURVIVAL_MODE;

            if (tryRestore(player, guide, history, mode)) return;

            history.clear();
            history.setMainMenuPage(1);
            renderer.openMainMenu(player, history, mode, 1);
        };

        if (guide != null && isCustomMode(guide)) {
            PlayerProfile.get(player, profile -> openTask.run());
        } else {
            openTask.run();
        }
    }

    public void handleGuideOpenFromReturn(Player player, ItemStack guide) {
        plugin.getExternalViewActive().remove(player.getUniqueId());
        CustomGuidePlugin.debug(player, "handleGuideOpenFromReturn: popping back to last category");
        plugin.getScgCloseDedup().remove(player.getUniqueId());

        CustomGuideHistory history = histories.get(player);
        if (history == null) {
            history = new CustomGuideHistory();
            histories.put(player, history);
            renderer.openMainMenu(player, history, SlimefunGuideMode.SURVIVAL_MODE, 1);
            return;
        }

        while (history.hasHistory() && !history.getCurrent().isCategory()) {
            history.goBack();
        }

        if (history.hasHistory()) {
            CustomGuideHistory.CategoryEntry last = (CustomGuideHistory.CategoryEntry) history.getCurrent();
            SlimefunGuideMode mode = findGuideMode(player);
            if (mode == null) mode = SlimefunGuideMode.SURVIVAL_MODE;
            renderer.openMenu(player, history, mode, last.getCategory(), last.getPage());
        } else {
            renderer.openMainMenu(player, history, SlimefunGuideMode.SURVIVAL_MODE, 1);
        }
    }

    public void pushNestedItem(Player player, String slimefunId) {
        CustomGuideHistory history = histories.get(player);
        if (history == null) return;
        history.pushItem(slimefunId);
        saveState(player, history);
        CustomGuidePlugin.debug(player, "pushNestedItem: pushed " + slimefunId + ", saved NBT");
    }

    public String navigateBackItem(Player player) {
        CustomGuideHistory history = histories.get(player);
        if (history == null || !history.hasHistory()) return null;

        history.goBack();

        if (!history.hasHistory() || history.getCurrent().isCategory()) {
            plugin.getExternalViewActive().remove(player.getUniqueId());
            CustomGuidePlugin.debug(player, "navigateBackItem: popped to category, clearing tracking");
            return null;
        }

        CustomGuideHistory.ItemEntry prev = (CustomGuideHistory.ItemEntry) history.getCurrent();
        saveState(player, history);
        CustomGuidePlugin.debug(player, "navigateBackItem: popped to " + prev.getSlimefunId());
        return prev.getSlimefunId();
    }

    public static boolean isCustomMode(ItemStack guide) {
        if (guide == null || !guide.hasItemMeta()) return false;
        return MODE_CUSTOM.equals(guide.getItemMeta().getPersistentDataContainer().get(MODE_KEY, PersistentDataType.STRING));
    }

    public static void setCustomMode(ItemStack guide) {
        if (guide == null) return;
        ItemMeta meta = guide.hasItemMeta() ? guide.getItemMeta() : org.bukkit.Bukkit.getItemFactory().getItemMeta(guide.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, MODE_CUSTOM);
        guide.setItemMeta(meta);
    }

    public static void clearCustomMode(ItemStack guide) {
        if (guide == null || !guide.hasItemMeta()) return;
        ItemMeta meta = guide.getItemMeta();
        meta.getPersistentDataContainer().remove(MODE_KEY);
        guide.setItemMeta(meta);
    }

    public void removeHistory(Player player) { histories.remove(player); }

    public CustomGuideHistory getHistory(Player player) { return histories.get(player); }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();

        boolean tracking = plugin.getExternalViewActive().contains(p.getUniqueId());

        if (tracking) {
            plugin.getScgMenuOpen().remove(p.getUniqueId());
            CustomGuidePlugin.debug(p, "onInventoryClose: tracking active, clearing scgMenuOpen");
            return;
        }

        if (plugin.getScgMenuOpen().remove(p.getUniqueId())) {
            if (!plugin.getScgCloseDedup().add(p.getUniqueId())) {
                CustomGuidePlugin.debug(p, "onInventoryClose: duplicate close event, skipped");
                return;
            }
            CustomGuidePlugin.debug(p, "onInventoryClose: SCG menu closing, saving state");
            CustomGuideHistory history = histories.get(p);
            if (history != null) saveState(p, history);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        histories.remove(e.getPlayer());
        plugin.getExternalViewActive().remove(e.getPlayer().getUniqueId());
        plugin.getScgMenuOpen().remove(e.getPlayer().getUniqueId());
        plugin.getScgCloseDedup().remove(e.getPlayer().getUniqueId());
        plugin.getSuppressPush().remove(e.getPlayer().getUniqueId());
    }

    private void saveState(Player player, CustomGuideHistory history) {
        ItemStack guide = findGuideItem(player);
        if (guide == null) return;
        ItemMeta meta = guide.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String breadcrumb = buildBreadcrumb(history);
        CustomGuidePlugin.debug(player, "saveState: NBT = " + breadcrumb);
        pdc.set(SAVE_KEY, PersistentDataType.STRING, breadcrumb);
        guide.setItemMeta(meta);
    }

    private String buildBreadcrumb(CustomGuideHistory history) {
        StringBuilder breadcrumb = new StringBuilder();
        breadcrumb.append(history.getMainMenuPage());
        Deque<CustomGuideHistory.HistoryEntry> stack = history.getStack();
        int idx = 0;
        int size = stack.size();
        for (CustomGuideHistory.HistoryEntry entry : stack) {
            idx++;
            if (entry.isCategory()) {
                CustomGuideHistory.CategoryEntry cat = (CustomGuideHistory.CategoryEntry) entry;
                int page = (idx == size) ? history.getCurrentPage() : entry.getPage();
                breadcrumb.append('|').append(cat.getCategory().getKey()).append(':').append(page);
            } else {
                CustomGuideHistory.ItemEntry item = (CustomGuideHistory.ItemEntry) entry;
                breadcrumb.append('|').append("ITEM:").append(item.getSlimefunId());
            }
        }
        if (breadcrumb.length() > MAX_BREADCRUMB_LEN) {
            breadcrumb.setLength(MAX_BREADCRUMB_LEN);
        }
        return breadcrumb.toString();
    }

    private boolean tryRestore(Player player, ItemStack guide, CustomGuideHistory history, SlimefunGuideMode mode) {
        if (guide == null || !guide.hasItemMeta()) return false;
        ItemMeta meta = guide.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String saved = pdc.get(SAVE_KEY, PersistentDataType.STRING);
        if (saved == null || saved.isEmpty()) return false;

        CustomGuidePlugin.debug(player, "tryRestore: NBT = " + saved);

        String[] parts = saved.split("\\|");
        int mainPage = 1;
        if (parts.length > 0) {
            try { mainPage = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        }
        history.setMainMenuPage(mainPage);

        String lastItemId = null;
        CustomCategory lastCategory = null;
        int lastCategoryPage = 1;
        boolean chainBroken = false;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("ITEM:")) {
                String itemId = part.substring(5);
                if (!itemId.isEmpty()) {
                    SlimefunItem sfItem = IconParser.findSlimefunItem(itemId);
                    if (sfItem == null) {
                        CustomGuidePlugin.debug(player, "tryRestore: item not found: " + itemId + ", chain broken");
                        chainBroken = true;
                        break;
                    }
                    history.pushItem(itemId);
                    lastItemId = itemId;
                }
            } else {
                String[] catParts = part.split(":");
                if (catParts.length == 0) continue;
                CustomCategory targetCat = findCategoryByKey(plugin.getRootCategories(), catParts[0]);
                if (targetCat == null) {
                    CustomGuidePlugin.debug(player, "tryRestore: category not found: " + catParts[0] + ", chain broken");
                    chainBroken = true;
                    break;
                }
                int catPage = 1;
                if (catParts.length > 1) {
                    try { catPage = Integer.parseInt(catParts[1]); } catch (NumberFormatException ignored) {}
                }
                history.push(targetCat, catPage);
                lastCategory = targetCat;
                lastCategoryPage = catPage;
                lastItemId = null;
            }
        }

        if (chainBroken) {
            clearSaveNbt(player);
            history.clear();
            history.setMainMenuPage(1);
            renderer.openMainMenu(player, history, mode, 1);
            return true;
        }

        if (lastCategory != null) {
            if (lastItemId != null) {
                CustomGuidePlugin.debug(player, "tryRestore: jumping directly to item " + lastItemId);
                final String finalItemId = lastItemId;
                final SlimefunGuideMode finalMode = mode;
                plugin.getExternalViewActive().add(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    PlayerProfile.get(player, profile -> {
                        SlimefunItem sfItem = IconParser.findSlimefunItem(finalItemId);
                        if (sfItem != null) {
                            plugin.getSuppressPush().add(player.getUniqueId());
                            try {
                                Slimefun.getRegistry().getSlimefunGuide(finalMode)
                                        .displayItem(profile, sfItem, true);
                            } finally {
                                plugin.getSuppressPush().remove(player.getUniqueId());
                            }
                        }
                    });
                });
            } else {
                renderer.openMenu(player, history, mode, lastCategory, lastCategoryPage);
            }
            return true;
        }

        renderer.openMainMenu(player, history, mode, mainPage);
        return true;
    }

    private CustomCategory findCategoryByKey(List<CustomCategory> roots, String key) {
        for (CustomCategory cat : roots) {
            if (cat.getKey().equals(key)) return cat;
            CustomCategory found = findCategoryByKey(getSubCategories(cat), key);
            if (found != null) return found;
        }
        return null;
    }

    private List<CustomCategory> getSubCategories(CustomCategory cat) {
        List<CustomCategory> subs = new ArrayList<>();
        for (GuideTreeNode child : cat.getChildren()) {
            if (child instanceof CustomCategory) subs.add((CustomCategory) child);
        }
        return subs;
    }

    private ItemStack findGuideItem(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR && isCustomMode(offHand)) return offHand;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isCustomMode(item)) return item;
        }
        return null;
    }

    private void clearSaveNbt(Player player) {
        ItemStack guide = findGuideItem(player);
        if (guide == null || !guide.hasItemMeta()) return;
        ItemMeta meta = guide.getItemMeta();
        meta.getPersistentDataContainer().remove(SAVE_KEY);
        guide.setItemMeta(meta);
    }

    private SlimefunGuideMode findGuideMode(Player player) {
        ItemStack guide = findGuideItem(player);
        if (guide == null) return null;
        return SlimefunUtils.isItemSimilar(
                guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false)
                ? SlimefunGuideMode.CHEAT_MODE
                : SlimefunGuideMode.SURVIVAL_MODE;
    }
}
