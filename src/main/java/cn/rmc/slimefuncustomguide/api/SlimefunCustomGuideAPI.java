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
package cn.rmc.slimefuncustomguide.api;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SlimefunCustomGuideAPI {

    private SlimefunCustomGuideAPI() {}

    private static CustomGuidePlugin plugin() {
        return CustomGuidePlugin.getInstance();
    }

    public static boolean isCustomGuideMode(ItemStack guide) {
        return CustomGuideListener.isCustomMode(guide);
    }

    public static void openOrRestore(Player player, ItemStack guide) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return;
        CustomGuidePlugin.debug(player, "API.openOrRestore: redirecting to SCG");
        pl.getGuideListener().handleGuideOpenFromReturn(player, guide);
    }

    public static void markExternalView(Player player) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return;
        CustomGuidePlugin.debug(player, "API.markExternalView: setting tracking flag");
        pl.getExternalViewActive().add(player.getUniqueId());
    }

    public static boolean isInExternalView(Player player) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return false;
        return pl.getExternalViewActive().contains(player.getUniqueId());
    }

    public static void pushNestedDetail(Player player, String slimefunId) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return;
        if (pl.getSuppressPush().contains(player.getUniqueId())) return;
        if (!pl.getExternalViewActive().contains(player.getUniqueId())) return;
        CustomGuidePlugin.debug(player, "API.pushNestedDetail: pushing " + slimefunId);
        pl.getGuideListener().pushNestedItem(player, slimefunId);
    }

    public static void suppressPush(Player player) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return;
        pl.getSuppressPush().add(player.getUniqueId());
    }

    public static void clearSuppressPush(Player player) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return;
        pl.getSuppressPush().remove(player.getUniqueId());
    }

    public static String navigateBackItem(Player player) {
        CustomGuidePlugin pl = plugin();
        if (pl == null) return null;
        return pl.getGuideListener().navigateBackItem(player);
    }
}
