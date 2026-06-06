package cn.rmc.slimefuncustomguide;

import cn.rmc.slimefuncustomguide.command.CustomGuideCommand;
import cn.rmc.slimefuncustomguide.config.CategoryConfigLoader;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
import cn.rmc.slimefuncustomguide.model.CustomCategory;
import cn.rmc.slimefuncustomguide.web.WebApiHandler;
import cn.rmc.slimefuncustomguide.web.WebServer;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

public final class CustomGuidePlugin extends JavaPlugin implements SlimefunAddon {

    private static CustomGuidePlugin instance;
    private List<CustomCategory> rootCategories;
    private WebServer webServer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("categories.yml", false);

        File file = new File(getDataFolder(), "categories.yml");
        rootCategories = CategoryConfigLoader.load(file, getLogger());
        getLogger().info("Loaded " + rootCategories.size() + " top-level categories");

        getServer().getPluginManager().registerEvents(new CustomGuideListener(this), this);

        getCommand("slimefuncustomguide").setExecutor(new CustomGuideCommand(this));

        if (getConfig().getBoolean("web-editor.enabled", true)) {
            String bind = getConfig().getString("web-editor.bind", "127.0.0.1");
            int port = getConfig().getInt("web-editor.port", 8899);
            webServer = new WebServer();
            WebApiHandler handler = new WebApiHandler(this);
            try {
                webServer.start(bind, port, handler);
                getLogger().info("Web editor started at http://" + bind + ":" + port);
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
        }
        getLogger().info("Reloaded " + rootCategories.size() + " root categories");
    }

    public List<CustomCategory> getRootCategories() {
        return Collections.unmodifiableList(rootCategories);
    }

    public boolean isCustomGuideEnabled() {
        return getConfig().getBoolean("enable-custom-guide", true);
    }

    public static CustomGuidePlugin getInstance() { return instance; }

    @Override public JavaPlugin getJavaPlugin() { return this; }
    @Override public String getBugTrackerURL() { return null; }
}
