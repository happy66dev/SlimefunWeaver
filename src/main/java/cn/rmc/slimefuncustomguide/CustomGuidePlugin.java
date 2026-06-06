package cn.rmc.slimefuncustomguide;

import cn.rmc.slimefuncustomguide.command.CustomGuideCommand;
import cn.rmc.slimefuncustomguide.config.CategoryConfigLoader;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
import cn.rmc.slimefuncustomguide.model.CustomCategory;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

public final class CustomGuidePlugin extends JavaPlugin implements SlimefunAddon {

    private static CustomGuidePlugin instance;
    private List<CustomCategory> rootCategories;

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
    }

    @Override
    public void onDisable() { instance = null; }

    public List<CustomCategory> getRootCategories() {
        return Collections.unmodifiableList(rootCategories);
    }

    public boolean isCustomGuideEnabled() {
        return getConfig().getBoolean("enable-custom-guide", true);
    }

    public void reloadCategories() {
        File file = new File(getDataFolder(), "categories.yml");
        this.rootCategories = CategoryConfigLoader.load(file, getLogger());
        getLogger().info("Reloaded " + rootCategories.size() + " root categories");
    }

    public static CustomGuidePlugin getInstance() { return instance; }

    @Override public JavaPlugin getJavaPlugin() { return this; }
    @Override public String getBugTrackerURL() { return null; }
}
