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
package cn.rmc.slimefuncustomguide.settings;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideOption;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomGuideModeOption implements SlimefunGuideOption<Boolean> {

    @Nonnull
    @Override
    public SlimefunAddon getAddon() {
        return CustomGuidePlugin.getInstance();
    }

    @Nonnull
    @Override
    public NamespacedKey getKey() {
        return CustomGuideListener.MODE_KEY;
    }

    @Nonnull
    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        boolean isCustom = CustomGuideListener.isCustomMode(guide);
        ItemStack item = new ItemStack(isCustom ? Material.COMMAND_BLOCK : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "\u6307\u5357\u5206\u7c7b\u6837\u5f0f: " + ChatColor.YELLOW + (isCustom ? "SCG\u6307\u5357" : "\u539f\u7248\u6307\u5357"));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add((isCustom ? ChatColor.GREEN : ChatColor.GRAY) + "SCG\u6307\u5357");
        lore.add((!isCustom ? ChatColor.GREEN : ChatColor.GRAY) + "\u539f\u7248\u6307\u5357");
        lore.add("");
        lore.add(ChatColor.GRAY + "\u21E8 " + ChatColor.YELLOW + "\u5355\u51fb\u5207\u6362\u5206\u7c7b\u6837\u5f0f");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return Optional.of(item);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onClick(Player p, ItemStack guide) {
        if (!isSlimefunGuide(guide)) return;
        if (CustomGuideListener.isCustomMode(guide)) {
            CustomGuideListener.clearCustomMode(guide);
            CustomGuidePlugin plugin = CustomGuidePlugin.getInstance();
            if (plugin != null && plugin.getGuideListener() != null) {
                plugin.getGuideListener().clearAllState(p);
            }
        } else {
            CustomGuideListener.setCustomMode(guide);
        }
        SlimefunGuideSettings.openSettings(p, guide);
    }

    @Nonnull
    @Override
    public Optional<Boolean> getSelectedOption(Player p, ItemStack guide) {
        return Optional.of(CustomGuideListener.isCustomMode(guide));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void setSelectedOption(Player p, ItemStack guide, Boolean value) {
        if (!isSlimefunGuide(guide)) return;
        if (Boolean.TRUE.equals(value)) {
            CustomGuideListener.setCustomMode(guide);
        } else {
            CustomGuideListener.clearCustomMode(guide);
        }
    }

    private boolean isSlimefunGuide(ItemStack guide) {
        return SlimefunUtils.isItemSimilar(guide, SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE), true, false)
                || SlimefunUtils.isItemSimilar(guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false);
    }
}
