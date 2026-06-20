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
package cn.rmc.slimefunweaver.guide;

import cn.rmc.slimefunweaver.SlimefunWeaver;
import cn.rmc.slimefunweaver.api.SlimefunWeaverAPI;
import cn.rmc.slimefunweaver.config.PlaceholderResolver;
import cn.rmc.slimefunweaver.model.CustomCategory;
import cn.rmc.slimefunweaver.model.CustomItemEntry;
import cn.rmc.slimefunweaver.model.CustomPlaceholderEntry;
import cn.rmc.slimefunweaver.model.CustomReferenceEntry;
import cn.rmc.slimefunweaver.model.GuideTreeNode;
import cn.rmc.slimefunweaver.model.IconSource;
import cn.rmc.slimefunweaver.model.IconType;
import cn.rmc.slimefunweaver.model.TreeNodeType;
import cn.rmc.slimefunweaver.util.IconParser;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.chat.ChatInput;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CustomGuideRenderer {

    private static final String ROOT_KEY = "§r§0_root";

    static final int CONTENT_SIZE = 36;
    static final int BACK_SLOT = 0;
    static final int SETTINGS_SLOT = 1;
    static final int SEARCH_SLOT = 7;
    static final int PREV_SLOT = 46;
    static final int NEXT_SLOT = 52;

    private final SlimefunWeaver plugin;

    public CustomGuideRenderer(SlimefunWeaver plugin) { this.plugin = plugin; }

    public void openMainMenu(Player player, CustomGuideHistory history,
                             SlimefunGuideMode mode, int page) {
        CustomCategory dummyRoot = new CustomCategory(ROOT_KEY, "Root",
                new IconSource(IconType.VANILLA, "BOOK"), null, 1, 0, false);
        for (CustomCategory cat : plugin.getRootCategories()) {
            dummyRoot.addChild(cat);
        }
        history.setCurrentCategory(dummyRoot);
        history.setCurrentPage(page);
        openMenu(player, history, mode, dummyRoot, page);
    }

    public void openMenu(Player player, CustomGuideHistory history,
                          SlimefunGuideMode mode, CustomCategory category, int page) {
        if (category == null) {
            openMainMenu(player, history, mode, 1);
            return;
        }
        history.setCurrentCategory(category);
        history.setCurrentPage(page);
        List<GuideTreeNode> children = category.getChildren();
        if (children == null) children = new ArrayList<>();
        int maxPage = calculateMaxPage(children);
        page = Math.max(1, Math.min(page, maxPage));

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(player, "guide.title.main"));
        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);

        renderHeader(player, menu, history, mode);
        if (children.isEmpty()) renderEmptyCategory(menu);
        renderContent(menu, children, player, history, mode, page, maxPage, category);
        renderFooter(menu, player, page, maxPage, history, mode, category);

        menu.open(player);
        plugin.getScgMenuOpen().add(player.getUniqueId());
        plugin.getScgCloseDedup().remove(player.getUniqueId());
    }

    private void renderHeader(Player player, ChestMenu menu,
                               CustomGuideHistory history, SlimefunGuideMode mode) {
        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        addBackButton(menu, BACK_SLOT, player, history, mode);

        menu.addItem(SETTINGS_SLOT, ChestMenuUtils.getMenuButton(player));
        menu.addMenuClickHandler(SETTINGS_SLOT, (pl, s, is, action) -> {
            SlimefunWeaverAPI.markExternalView(pl);
            SlimefunGuideSettings.openSettings(pl, pl.getInventory().getItemInMainHand());
            return false;
        });

        menu.addItem(SEARCH_SLOT, ChestMenuUtils.getSearchButton(player));
        menu.addMenuClickHandler(SEARCH_SLOT, (pl, s, is, action) -> {
            pl.closeInventory();
            Slimefun.getLocalization().sendMessage(pl, "guide.search.message");
            SlimefunWeaverAPI.markExternalView(pl);
            ChatInput.waitForPlayer(Slimefun.instance(), pl, msg -> {
                PlayerProfile.get(pl, profile ->
                        SlimefunGuide.openSearch(profile, msg, mode, true));
            });
            return false;
        });
    }

    private void addBackButton(ChestMenu menu, int slot, Player player,
                                CustomGuideHistory history, SlimefunGuideMode mode) {
        if (history.hasHistory()) {
            menu.addItem(slot,
                    new CustomItemStack(ChestMenuUtils.getBackButton(player, "",
                            "&f\u5de6\u952e: &7\u8fd4\u56de\u4e0a\u4e00\u9875",
                            "&fShift + \u5de6\u952e: &7\u8fd4\u56de\u6839\u76ee\u5f55")));
            menu.addMenuClickHandler(slot, (pl, s1, is1, action1) -> {
                if (action1.isShiftClicked()) {
                    history.clear();
                    openMainMenu(pl, history, mode, history.getMainMenuPage());
                } else {
                    CustomGuideHistory.HistoryEntry back = history.goBack();
                    if (back != null && back.isCategory()) {
                        CustomGuideHistory.CategoryEntry catBack = (CustomGuideHistory.CategoryEntry) back;
                        openMenu(pl, history, mode, catBack.getCategory(), catBack.getPage());
                    } else {
                        history.clear();
                        openMainMenu(pl, history, mode, history.getMainMenuPage());
                    }
                }
                return false;
            });
        } else {
            // 喵~无历史时用 SF4 的 _UI_BACKGROUND 占位符，支持自定义材质喵
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    private void renderContent(ChestMenu menu, List<GuideTreeNode> children,
                                Player player, CustomGuideHistory history,
                                SlimefunGuideMode mode, int page, int maxPage,
                                CustomCategory category) {
        for (GuideTreeNode child : children) {
            if (child.getPage() != page) continue;
            int slot = child.getSlot();
            if (slot < 0 || slot >= CONTENT_SIZE) continue;
            int absSlot = 9 + slot;

            ItemStack displayItem;
            if (child.getType() == TreeNodeType.CATEGORY) {
                displayItem = buildCategoryItem((CustomCategory) child, player, page, maxPage);
            } else if (child.getType() == TreeNodeType.ITEM) {
                displayItem = ((CustomItemEntry) child).getIcon(player);
            } else if (child.getType() == TreeNodeType.REFERENCE) {
                displayItem = buildReferenceItem((CustomReferenceEntry) child, player, page, maxPage);
            } else {
                displayItem = buildPlaceholderItem((CustomPlaceholderEntry) child);
            }

            menu.addItem(absSlot, displayItem);

            if (child.getType() == TreeNodeType.CATEGORY) {
                CustomCategory cat = (CustomCategory) child;
                menu.addMenuClickHandler(absSlot, (pl, s, is, action) -> {
                    history.push(cat, page);
                    openMenu(pl, history, mode, cat, 1);
                    return false;
                });
            } else if (child.getType() == TreeNodeType.REFERENCE) {
                CustomReferenceEntry ref = (CustomReferenceEntry) child;
                menu.addMenuClickHandler(absSlot, (pl, s, is, action) -> {
                    CustomCategory target = findCategoryByKey(plugin.getRootCategories(), ref.getTargetCategoryKey());
                    if (target != null) {
                        history.pushForce(category, page);
                        openMenu(pl, history, mode, target, 1);
                    }
                    return false;
                });
            } else if (child.getType() == TreeNodeType.ITEM) {
                menu.addMenuClickHandler(absSlot, (pl, s, is, action) -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        PlayerProfile.get(pl, profile -> {
                            SlimefunItem slimefunItem = ((CustomItemEntry) child).getSlimefunItem();
                            if (mode == SlimefunGuideMode.CHEAT_MODE) {
                                handleCheatItemClick(pl, slimefunItem, action.isShiftClicked());
                            } else {
                                SlimefunWeaverAPI.markExternalView(pl);
                                Slimefun.getRegistry().getSlimefunGuide(mode).displayItem(profile, slimefunItem, true);
                            }
                        });
                    });
                    return false;
                });
            } else {
                menu.addMenuClickHandler(absSlot, ChestMenuUtils.getEmptyClickHandler());
            }
        }
    }

    private void handleCheatItemClick(Player player, SlimefunItem item, boolean fullStack) {
        if (!player.hasPermission("slimefun.cheat.items")) {
            Slimefun.getLocalization().sendMessage(player, "messages.no-permission", true);
            return;
        }
        if (item instanceof MultiBlockMachine) {
            Slimefun.getLocalization().sendMessage(player, "guide.cheat.no-multiblocks");
            return;
        }
        ItemStack clonedItem = item.getItem().clone();
        if (fullStack) clonedItem.setAmount(clonedItem.getMaxStackSize());
        player.getInventory().addItem(clonedItem);
    }

    private void renderEmptyCategory(ChestMenu menu) {
        menu.addItem(31, new CustomItemStack(new ItemStack(Material.BARRIER), ChatColor.RED + "空分类", ChatColor.GRAY + "这个分类下暂时没有内容"));
        menu.addMenuClickHandler(31, ChestMenuUtils.getEmptyClickHandler());
    }

    private ItemStack buildCategoryItem(CustomCategory category, Player player,
                                         int page, int maxPage) {
        Optional<ItemStack> optIcon = IconParser.parse(category.getIconSource(), plugin.getLogger(), category.isGlow());
        ItemStack icon = optIcon.orElse(new ItemStack(Material.BOOK));

        return new CustomItemStack(icon, meta -> {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', category.getDisplay()));
            List<String> lore = PlaceholderResolver.resolve(category, page, maxPage);
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            if (!colored.isEmpty()) meta.setLore(colored);
            else meta.setLore(null);
        });
    }

    private ItemStack buildPlaceholderItem(CustomPlaceholderEntry entry) {
        Optional<ItemStack> optIcon = IconParser.parse(entry.getIconSource(), plugin.getLogger(), entry.isGlow());
        ItemStack icon = optIcon.orElse(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        return new CustomItemStack(icon, meta -> {
            String display = entry.getDisplay();
            if (display != null && !display.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', display));
            }
            List<String> lore = entry.getLore();
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }
        });
    }

    private void renderFooter(ChestMenu menu, Player player, int page,
                               int maxPage, CustomGuideHistory history,
                               SlimefunGuideMode mode, CustomCategory category) {
        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        if (maxPage > 1) {
            boolean isRoot = category.getKey().equals(ROOT_KEY);
            menu.addItem(PREV_SLOT, ChestMenuUtils.getPreviousButton(player, page, maxPage));
            menu.addMenuClickHandler(PREV_SLOT, (pl, s, is, action) -> {
                int next = page - 1;
                if (next > 0) {
                    if (isRoot) { history.setMainMenuPage(next); openMainMenu(pl, history, mode, next); }
                    else openMenu(pl, history, mode, category, next);
                }
                return false;
            });

            menu.addItem(NEXT_SLOT, ChestMenuUtils.getNextButton(player, page, maxPage));
            menu.addMenuClickHandler(NEXT_SLOT, (pl, s, is, action) -> {
                int next = page + 1;
                if (next <= maxPage) {
                    if (isRoot) { history.setMainMenuPage(next); openMainMenu(pl, history, mode, next); }
                    else openMenu(pl, history, mode, category, next);
                }
                return false;
            });
        }
    }

    private ItemStack buildReferenceItem(CustomReferenceEntry entry, Player player, int page, int maxPage) {
        CustomCategory target = entry.isCopyMode() ? findCategoryByKey(plugin.getRootCategories(), entry.getTargetCategoryKey()) : null;
        boolean useTarget = target != null && entry.isCopyMode();

        IconSource effectiveIcon;
        if (useTarget && target.getIconSource() != null) {
            effectiveIcon = target.getIconSource();
        } else {
            effectiveIcon = entry.getIconSource();
        }
        boolean effectiveGlow = useTarget ? target.isGlow() : entry.isGlow();

        String resolvedDisplay;
        if (useTarget && !entry.hasCustomDisplay()) {
            resolvedDisplay = target.getDisplay();
            if (resolvedDisplay == null || resolvedDisplay.isEmpty()) resolvedDisplay = target.getKey();
        } else if (entry.hasCustomDisplay()) {
            resolvedDisplay = entry.getRawDisplay();
        } else {
            resolvedDisplay = entry.getDisplay();
        }

        List<String> resolvedLore;
        if (entry.hasCustomLore()) {
            resolvedLore = entry.getRawLore();
        } else if (useTarget) {
            List<String> rawTargetLore = target.getLore();
            if (!rawTargetLore.isEmpty()) {
                resolvedLore = PlaceholderResolver.resolve(target, 1, calculateMaxPage(target.getChildren()));
            } else {
                resolvedLore = Collections.emptyList();
            }
        } else {
            resolvedLore = entry.getRawLore();
        }

        final String finalDisplay = resolvedDisplay;
        final List<String> finalLore = resolvedLore;

        Optional<ItemStack> optIcon = IconParser.parse(effectiveIcon, plugin.getLogger(), effectiveGlow);
        ItemStack icon = optIcon.orElse(new ItemStack(Material.ARROW));

        return new CustomItemStack(icon, meta -> {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', finalDisplay));
            if (finalLore != null && !finalLore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : finalLore) colored.add(ChatColor.translateAlternateColorCodes('&', line));
                meta.setLore(colored);
            } else {
                meta.setLore(null);
            }
        });
    }

    private static CustomCategory findCategoryByKey(List<CustomCategory> roots, String key) {
        String[] parts = key.split("/");
        List<CustomCategory> currentLevel = roots;
        CustomCategory found = null;
        for (int pi = 0; pi < parts.length; pi++) {
            String part = parts[pi];
            boolean matched = false;
            for (CustomCategory cat : currentLevel) {
                if (cat.getKey().equals(part)) {
                    matched = true;
                    found = cat;
                    List<CustomCategory> children = new ArrayList<>();
                    for (GuideTreeNode child : cat.getChildren()) {
                        if (child instanceof CustomCategory) children.add((CustomCategory) child);
                    }
                    currentLevel = children;
                    break;
                }
            }
            if (!matched) return null;
        }
        return found;
    }

    int calculateMaxPage(List<GuideTreeNode> children) {
        int max = 1;
        for (GuideTreeNode child : children) {
            if (child.getPage() > max) max = child.getPage();
        }
        return max;
    }
}
