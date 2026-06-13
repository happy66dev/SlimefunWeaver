// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy (k666kkk666k@163.com)
package cn.rmc.slimefunweaver.research;

import cn.rmc.slimefunweaver.SlimefunWeaver;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class CustomResearchManager {

    public static final String RESEARCH_PREFIX = "SWR_";
    
    private static SlimefunWeaver plugin;
    private static File configFile;
    private static boolean initialized = false;
    // Research ID 从 100000 开始递增，避免与原版 Slimefun 研究 ID (通常 < 10000) 冲突
    private static final AtomicInteger nextResearchId = new AtomicInteger(100000);

    public static void initialize(SlimefunWeaver pluginInstance) {
        if (initialized) {
            return;
        }
        plugin = pluginInstance;
        configFile = new File(plugin.getDataFolder(), "CustomResearches.yml");
        
        if (!configFile.exists()) {
            plugin.getLogger().info("CustomResearches.yml not found, importing from vanilla...");
            importFromVanilla();
        }
        
        loadAndRegister();
        initialized = true;
    }

    private static void importFromVanilla() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        ConfigurationSection researchesSec = config.createSection("researches");
        
        Map<String, Set<String>> researchItems = new HashMap<>();
        Map<String, Set<String>> itemToParents = new HashMap<>();
        
        for (Research r : Slimefun.getRegistry().getResearches()) {
            try {
                String originalKey = r.getKey().toString();
                String[] parts = originalKey.split(":", 2);
                String namespace = parts.length > 1 ? parts[0] : "slimefun";
                String key = parts.length > 1 ? parts[1] : parts[0];
                String customKey = namespace + ":" + RESEARCH_PREFIX + key;
                
                String researchName = r.getUnlocalizedName();
                Map<String, String> localizedNames = getLocalizedNames(r);
                
                ConfigurationSection sec = researchesSec.createSection(customKey);
                sec.set("name", researchName);
                if (!localizedNames.isEmpty()) {
                    ConfigurationSection namesSec = sec.createSection("names");
                    for (Map.Entry<String, String> entry : localizedNames.entrySet()) {
                        namesSec.set(entry.getKey(), entry.getValue());
                    }
                }
                sec.set("level-cost", r.getLevelCost());
                sec.set("money-cost", r.getMoneyCost());
                sec.set("enabled", true);
                
                List<String> itemIds = new ArrayList<>();
                for (SlimefunItem item : r.getAffectedItems()) {
                    itemIds.add(item.getId());
                }
                sec.set("items", itemIds);
                
                researchItems.put(customKey, new HashSet<>(itemIds));
                
                for (SlimefunItem needItem : r.getNeedUnlockedItems()) {
                    String needItemId = needItem.getId();
                    itemToParents.computeIfAbsent(needItemId, k -> new HashSet<>()).add(originalKey);
                }
                
                sec.set("parents", new ArrayList<>());
                
                Map<String, Object> skills = new LinkedHashMap<>();
                skills.put("FARMING", r.getFarmingLevelNeed());
                skills.put("FORAGING", r.getForagingLevelNeed());
                skills.put("MINING", r.getMiningLevelNeed());
                skills.put("FISHING", r.getFishingLevelNeed());
                skills.put("EXCAVATION", r.getExcavationLevelNeed());
                skills.put("ARCHERY", r.getArcheryLevelNeed());
                skills.put("DEFENSE", r.getDefenseLevelNeed());
                skills.put("FIGHTING", r.getFightingLevelNeed());
                skills.put("AGILITY", r.getAgilityLevelNeed());
                skills.put("ENCHANTING", r.getEnchantingLevelNeed());
                skills.put("ALCHEMY", r.getAlchemyLevelNeed());
                sec.createSection("skills", skills);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to import research: " + r.getKey(), e);
            }
        }
        
        for (String customKey : researchItems.keySet()) {
            Set<String> items = researchItems.get(customKey);
            List<String> parents = new ArrayList<>();
            for (String itemId : items) {
                Set<String> parentKeys = itemToParents.get(itemId);
                if (parentKeys != null) {
                    for (String originalParentKey : parentKeys) {
                        String[] parts = originalParentKey.split(":", 2);
                        String ns = parts.length > 1 ? parts[0] : "slimefun";
                        String k = parts.length > 1 ? parts[1] : parts[0];
                        String customParentKey = ns + ":" + RESEARCH_PREFIX + k;
                        if (!customParentKey.equals(customKey) && !parents.contains(customParentKey)) {
                            parents.add(customParentKey);
                        }
                    }
                }
            }
            ConfigurationSection sec = researchesSec.getConfigurationSection(customKey);
            if (sec != null) sec.set("parents", parents);
        }
        
        try {
            config.save(configFile);
            plugin.getLogger().info("Imported " + researchItems.size() + " researches to CustomResearches.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save CustomResearches.yml", e);
        }
    }

    public static void resetAndReimport() throws Exception {
        if (configFile.exists()) {
            configFile.delete();
        }
        initialized = false;
        nextResearchId.set(100000);
        loadAndRegister();
        initialized = true;
    }
    
    private static Map<String, String> getLocalizedNames(Research research) {
        Map<String, String> names = new LinkedHashMap<>();
        try {
            for (org.bukkit.entity.Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String langCode = Slimefun.getLocalization().getLanguage(onlinePlayer).getId();
                String localizedName = research.getName(onlinePlayer);
                if (!localizedName.equals(research.getUnlocalizedName())) {
                    names.put(langCode, localizedName);
                }
            }
        } catch (Exception ignored) {}
        return names;
    }
    
    private static void registerLocalizedNames(NamespacedKey researchKey, ConfigurationSection namesSec) {
        try {
            File langDir = new File(plugin.getDataFolder().getParentFile(), "Slimefun/languages/researches");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }
            
            for (String langCode : namesSec.getKeys(false)) {
                String name = namesSec.getString(langCode);
                if (name != null && !name.isEmpty()) {
                    File langFile = new File(langDir, langCode + ".yml");
                    YamlConfiguration langConfig = langFile.exists() 
                        ? YamlConfiguration.loadConfiguration(langFile) 
                        : new YamlConfiguration();
                    langConfig.set(researchKey.getNamespace() + "." + researchKey.getKey(), name);
                    langConfig.save(langFile);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to register localized names for " + researchKey, e);
        }
    }
    
    private static void loadAndRegister() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.getBoolean("enabled", false)) {
            plugin.getLogger().info("Custom research system is disabled in config");
            return;
        }
        
        ConfigurationSection researchesSection = config.getConfigurationSection("researches");
        if (researchesSection == null) {
            plugin.getLogger().warning("No researches found in CustomResearches.yml");
            return;
        }
        
        Map<String, ConfigurationSection> researchConfigs = new LinkedHashMap<>();
        for (String fullKey : researchesSection.getKeys(false)) {
            ConfigurationSection sec = researchesSection.getConfigurationSection(fullKey);
            if (sec != null) {
                researchConfigs.put(fullKey, sec);
            }
        }
        
        for (Map.Entry<String, ConfigurationSection> entry : researchConfigs.entrySet()) {
            try {
                registerOne(entry.getKey(), entry.getValue(), researchConfigs);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to register research: " + entry.getKey(), e);
            }
        }
        
        plugin.getLogger().info("Registered " + researchConfigs.size() + " custom researches");
    }

    private static void registerOne(String fullKey, ConfigurationSection config, Map<String, ConfigurationSection> allConfigs) {
        String[] parts = fullKey.split(":", 2);
        String namespace = parts.length > 1 ? parts[0] : "slimefun";
        String key = parts.length > 1 ? parts[1] : parts[0];
        
        NamespacedKey nsKey = new NamespacedKey(namespace, key);
        String name = config.getString("name", key);
        int levelCost = config.getInt("level-cost", 0);
        double moneyCost = config.getDouble("money-cost", 0.0);
        boolean enabled = config.getBoolean("enabled", true);
        
        if (!enabled) {
            return;
        }
        
        Research research = new Research(nsKey, nextResearchId.getAndIncrement(), name, levelCost);
        research.setMoneyCost(moneyCost);
        
        ConfigurationSection namesSec = config.getConfigurationSection("names");
        if (namesSec != null) {
            registerLocalizedNames(nsKey, namesSec);
        }
        
        List<String> itemIds = config.getStringList("items");
        List<SlimefunItem> items = new ArrayList<>();
        for (String itemId : itemIds) {
            SlimefunItem item = SlimefunItem.getById(itemId);
            if (item != null) {
                items.add(item);
            }
        }
        
        List<String> parentKeys = config.getStringList("parents");
        for (String parentKey : parentKeys) {
            ConfigurationSection parentConfig = allConfigs.get(parentKey);
            if (parentConfig == null) {
                plugin.getLogger().warning("研究 " + fullKey + " 的父依赖 " + parentKey + " 不存在，已跳过");
                continue;
            }
            List<String> parentItemIds = parentConfig.getStringList("items");
            if (parentItemIds.isEmpty()) {
                plugin.getLogger().warning("研究 " + fullKey + " 的父依赖 " + parentKey + " 没有物品，已跳过");
                continue;
            }
            for (String parentItemId : parentItemIds) {
                SlimefunItem parentItem = SlimefunItem.getById(parentItemId);
                if (parentItem != null) {
                    research.addNeedUnlockedItems(parentItem);
                }
            }
        }
        
        ConfigurationSection skills = config.getConfigurationSection("skills");
        if (skills != null) {
            research.setFarmingLevelNeed(skills.getInt("FARMING", 0));
            research.setForagingLevelNeed(skills.getInt("FORAGING", 0));
            research.setMiningLevelNeed(skills.getInt("MINING", 0));
            research.setFishingLevelNeed(skills.getInt("FISHING", 0));
            research.setExcavationLevelNeed(skills.getInt("EXCAVATION", 0));
            research.setArcheryLevelNeed(skills.getInt("ARCHERY", 0));
            research.setDefenseLevelNeed(skills.getInt("DEFENSE", 0));
            research.setFightingLevelNeed(skills.getInt("FIGHTING", 0));
            research.setAgilityLevelNeed(skills.getInt("AGILITY", 0));
            research.setEnchantingLevelNeed(skills.getInt("ENCHANTING", 0));
            research.setAlchemyLevelNeed(skills.getInt("ALCHEMY", 0));
        }
        
        research.addItems(items.toArray(new SlimefunItem[0]));
        research.register();
    }
    
    public static boolean researchExists(String fullKey) {
        if (!configFile.exists()) {
            plugin.getLogger().info("[CRM] researchExists: configFile not exists");
            return false;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection sec = config.getConfigurationSection("researches");
        if (sec == null) {
            plugin.getLogger().info("[CRM] researchExists: researches section is null");
            return false;
        }
        Set<String> keys = sec.getKeys(false);
        plugin.getLogger().info("[CRM] researchExists: looking for '" + fullKey + "' in keys=" + keys);
        boolean found = sec.contains(fullKey);
        plugin.getLogger().info("[CRM] researchExists: found=" + found);
        return found;
    }
    
    public static boolean isResearchEnabled(String fullKey, boolean fallback) {
        if (plugin == null || fullKey == null) return fallback;
        if (!configFile.exists()) return fallback;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection sec = config.getConfigurationSection("researches");
        if (sec == null) return fallback;
        ConfigurationSection r = sec.getConfigurationSection(fullKey);
        if (r == null) return fallback;
        return r.contains("enabled") ? r.getBoolean("enabled") : fallback;
    }
    
    public static void createResearch(String fullKey, String name) throws Exception {
        YamlConfiguration config = configFile.exists() ? YamlConfiguration.loadConfiguration(configFile) : new YamlConfiguration();
        
        if (!config.contains("enabled")) {
            config.set("enabled", true);
        }
        
        ConfigurationSection researches = config.contains("researches") 
            ? config.getConfigurationSection("researches") 
            : config.createSection("researches");
        if (researches == null) researches = config.createSection("researches");
        ConfigurationSection sec = researches.createSection(fullKey);
        sec.set("name", name);
        sec.set("level-cost", 0);
        sec.set("money-cost", 0.0);
        sec.set("enabled", true);
        sec.set("items", new ArrayList<>());
        sec.set("parents", new ArrayList<>());
        
        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("FARMING", 0);
        skills.put("FORAGING", 0);
        skills.put("MINING", 0);
        skills.put("FISHING", 0);
        skills.put("EXCAVATION", 0);
        skills.put("ARCHERY", 0);
        skills.put("DEFENSE", 0);
        skills.put("FIGHTING", 0);
        skills.put("AGILITY", 0);
        skills.put("ENCHANTING", 0);
        skills.put("ALCHEMY", 0);
        sec.createSection("skills", skills);
        
        config.save(configFile);
    }
    
    public static void deleteResearch(String fullKey) throws Exception {
        if (!configFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        ConfigurationSection researchesSection = config.getConfigurationSection("researches");
        if (researchesSection == null) return;
        researchesSection.set(fullKey, null);
        
        for (String otherKey : researchesSection.getKeys(false)) {
            ConfigurationSection other = researchesSection.getConfigurationSection(otherKey);
            if (other == null) continue;
            List<String> parents = other.getStringList("parents");
            if (parents.remove(fullKey)) {
                other.set("parents", parents);
            }
        }
        
        config.save(configFile);
    }
    
    /**
     * 批量保存研究数据到 CustomResearches.yml
     * 注意: 此方法只更新传入的研究，不会删除配置文件中其他已存在的研究
     * 
     * @param researches 要保存的研究列表
     * @throws Exception 保存失败时抛出异常
     */
    public static void saveAllResearches(List<ResearchData> researches) throws Exception {
        YamlConfiguration config = configFile.exists() ? YamlConfiguration.loadConfiguration(configFile) : new YamlConfiguration();
        
        if (!config.contains("enabled")) {
            config.set("enabled", true);
        }
        
        ConfigurationSection researchesSec = config.contains("researches")
            ? config.getConfigurationSection("researches")
            : config.createSection("researches");
        if (researchesSec == null) researchesSec = config.createSection("researches");
        
        for (ResearchData data : researches) {
            ConfigurationSection sec = researchesSec.contains(data.fullKey)
                ? researchesSec.getConfigurationSection(data.fullKey)
                : researchesSec.createSection(data.fullKey);
            if (sec == null) sec = researchesSec.createSection(data.fullKey);
            sec.set("name", data.name);
            sec.set("level-cost", data.levelCost);
            sec.set("money-cost", data.moneyCost);
            sec.set("enabled", data.enabled);
            sec.set("items", data.items);
            sec.set("parents", data.parents);
            
            Map<String, Object> skills = new LinkedHashMap<>();
            skills.put("FARMING", data.farmingLevelNeed);
            skills.put("FORAGING", data.foragingLevelNeed);
            skills.put("MINING", data.miningLevelNeed);
            skills.put("FISHING", data.fishingLevelNeed);
            skills.put("EXCAVATION", data.excavationLevelNeed);
            skills.put("ARCHERY", data.archeryLevelNeed);
            skills.put("DEFENSE", data.defenseLevelNeed);
            skills.put("FIGHTING", data.fightingLevelNeed);
            skills.put("AGILITY", data.agilityLevelNeed);
            skills.put("ENCHANTING", data.enchantingLevelNeed);
            skills.put("ALCHEMY", data.alchemyLevelNeed);
            sec.createSection("skills", skills);
        }
        
        config.save(configFile);
    }
    
    public static class ResearchData {
        public String fullKey;
        public String name;
        public int levelCost;
        public double moneyCost;
        public boolean enabled;
        public List<String> items;
        public List<String> parents;
        public int miningLevelNeed;
        public int farmingLevelNeed;
        public int foragingLevelNeed;
        public int fishingLevelNeed;
        public int excavationLevelNeed;
        public int archeryLevelNeed;
        public int defenseLevelNeed;
        public int fightingLevelNeed;
        public int agilityLevelNeed;
        public int enchantingLevelNeed;
        public int alchemyLevelNeed;
    }
}
