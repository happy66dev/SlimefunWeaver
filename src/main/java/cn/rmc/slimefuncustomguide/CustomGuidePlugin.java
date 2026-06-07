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
package cn.rmc.slimefuncustomguide;

import cn.rmc.slimefuncustomguide.command.CustomGuideCommand;
import cn.rmc.slimefuncustomguide.config.CategoryConfigLoader;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
import cn.rmc.slimefuncustomguide.model.CustomCategory;
import cn.rmc.slimefuncustomguide.settings.CustomGuideModeOption;
import cn.rmc.slimefuncustomguide.web.RecipeApiHandler;
import cn.rmc.slimefuncustomguide.web.WebApiHandler;
import cn.rmc.slimefuncustomguide.web.WebServer;
import cn.rmc.slimefuncustomguide.util.VanillaMaterialLocalization;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomGuidePlugin extends JavaPlugin implements SlimefunAddon {

    private static CustomGuidePlugin instance;
    private volatile List<CustomCategory> rootCategories = Collections.emptyList();
    private WebServer webServer;
    private CustomGuideListener guideListener;
    private final Set<UUID> externalViewActive = ConcurrentHashMap.newKeySet();
    private final Set<UUID> scgMenuOpen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> scgCloseDedup = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressPush = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("categories.yml", false);

        VanillaMaterialLocalization.initialize();

        File file = new File(getDataFolder(), "categories.yml");
        rootCategories = CategoryConfigLoader.load(file, getLogger());
        getLogger().info("Loaded " + rootCategories.size() + " top-level categories");

        guideListener = new CustomGuideListener(this);
        getServer().getPluginManager().registerEvents(guideListener, this);

        SlimefunGuideSettings.addOption(new CustomGuideModeOption());

        if (getConfig().getBoolean("web-editor.auto-load-recipes", true)) {
            getServer().getScheduler().runTask(this, () -> RecipeApiHandler.loadRecipesOnStartup(this));
        }

        CustomGuideCommand cmd = new CustomGuideCommand(this);
        getCommand("slimefuncustomguide").setExecutor(cmd);
        getCommand("slimefuncustomguide").setTabCompleter(cmd);

        if (getConfig().getBoolean("web-editor.enabled", false)) {
            String bind = getConfig().getString("web-editor.bind", "127.0.0.1");
            int port = getConfig().getInt("web-editor.port", 8899);
            String token = getConfig().getString("web-editor.token", "");
            if (token == null || token.isEmpty()) {
                getLogger().warning("Web editor is not started: web-editor.token is required");
                return;
            }
            if (!cn.rmc.slimefuncustomguide.web.WebSecurity.isSafeBind(bind) && (token == null || token.isEmpty())) {
                getLogger().warning("Web editor is not started: non-local bind requires web-editor.token");
                return;
            }
            webServer = new WebServer();
            WebApiHandler handler = new WebApiHandler(this);
            boolean catEnabled = getConfig().getBoolean("web-editor.editors.categories", false);
            boolean recEnabled = getConfig().getBoolean("web-editor.editors.recipes", false);
            boolean resEnabled = getConfig().getBoolean("web-editor.editors.researches", false);
            try {
                webServer.start(bind, port, handler, catEnabled, recEnabled, resEnabled);
                String base = "http://" + bind + ":" + port;
                StringBuilder sb = new StringBuilder("Web editor started at ").append(base);
                if (catEnabled) sb.append("  分类编辑器: ").append(base).append("/");
                if (recEnabled) sb.append("  配方编辑器: ").append(base).append("/recipes.html");
                if (resEnabled) sb.append("  研究编辑器: ").append(base).append("/editor.html");
                getLogger().info(sb.toString());
            } catch (Exception e) {
                getLogger().warning("Failed to start web editor: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
            getLogger().info("Web editor stopped");
        }
        instance = null;
    }

    public void reloadCategories() {
        File file = new File(getDataFolder(), "categories.yml");
        this.rootCategories = CategoryConfigLoader.load(file, getLogger());
        if (rootCategories.isEmpty()) {
            getLogger().warning("categories.yml is empty or missing");
        } else {
            getLogger().info("Reloaded " + rootCategories.size() + " root categories");
        }
    }

    public List<CustomCategory> getRootCategories() {
        if (rootCategories == null) return Collections.emptyList();
        return Collections.unmodifiableList(rootCategories);
    }

    public boolean isCustomGuideEnabled() {
        return getConfig().getBoolean("enable-custom-guide", true);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug", false);
    }

    public static void debug(Player player, String msg) {
        CustomGuidePlugin pl = getInstance();
        if (pl == null || !pl.isDebugEnabled()) return;
        player.sendMessage("§8[§6SCG-Debug§8] §7" + msg);
    }

    public CustomGuideListener getGuideListener() { return guideListener; }

    public Set<UUID> getExternalViewActive() { return externalViewActive; }
    public Set<UUID> getScgMenuOpen() { return scgMenuOpen; }
    public Set<UUID> getScgCloseDedup() { return scgCloseDedup; }
    public Set<UUID> getSuppressPush() { return suppressPush; }

    public static CustomGuidePlugin getInstance() { return instance; }

    @Override public JavaPlugin getJavaPlugin() { return this; }
    @Override public String getBugTrackerURL() { return ""; }
}
