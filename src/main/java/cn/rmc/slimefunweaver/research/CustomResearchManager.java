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

    public static final String RESEARCH_PREFIX = "swr_";
    
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

    public static void clearAllResearches() throws Exception {
        plugin.getLogger().info("[CRM] CLEAR configFile.exists=" + configFile.exists());
        YamlConfiguration empty = new YamlConfiguration();
        empty.set("enabled", true);
        empty.createSection("researches");
        empty.save(configFile);
        initialized = false;
        nextResearchId.set(100000);
        plugin.getLogger().info("[CRM] CLEAR saved empty config, reloading");
        loadAndRegister();
        initialized = true;
        plugin.getLogger().info("[CRM] CLEAR done");
    }
    
    private static Map<String, String> getLocalizedNames(Research research) {
        Map<String, String> names = new LinkedHashMap<>();
        try {
            for (org.bukkit.entity.Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                io.github.thebusybiscuit.slimefun4.core.services.localization.Language lang = Slimefun.getLocalization().getLanguage(onlinePlayer);
                if (lang == null) continue;
                String langCode = lang.getId();
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
    
    private static int unregisterOldCustomResearches() {
        // 喵~只移除带 swr_ 前缀的自定义研究（旧版占位符清理）
        int removed = 0;
        java.util.Iterator<Research> it = Slimefun.getRegistry().getResearches().iterator();
        while (it.hasNext()) {
            Research r = it.next();
            try {
                String rKey = r.getKey().getKey();
                if (rKey.startsWith(RESEARCH_PREFIX)) {
                    // 喵~解绑该研究关联的所有物品，避免物品状态残留
                    for (SlimefunItem item : new ArrayList<>(r.getAffectedItems())) {
                        if (item != null && item.getResearch() == r) {
                            item.setResearch(null);
                        }
                    }
                    it.remove();
                    removed++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[CRM] Failed to unregister research", e);
            }
        }
        return removed;
    }

    /**
     * 当自定义研究系统启用时，将所有不在 CustomResearches.yml 中的研究从 SF4 注册表移除。
     * 这样可以阻止 addon 通过 Research.register() 绕过自定义研究配置的研究出现在游戏中。
     *
     * 输入：allowedKeys — CustomResearches.yml 的 researches 节点中配置的所有 fullKey（namespace:key 格式）
     * 输出：被移除的研究数量
     * 边界条件：allowedKeys 为空 → 移除所有非 swr_ 研究（白名单为空即全部拦截）
     *
     * @param allowedKeys 允许保留的研究 key 集合（来自 CustomResearches.yml）
     * @return 被移除的研究数量
     */
    private static int unregisterNonCustomResearches(Set<String> allowedKeys) {
        int removed = 0;
        java.util.Iterator<Research> it = Slimefun.getRegistry().getResearches().iterator();
        while (it.hasNext()) {
            Research r = it.next();
            try {
                // 喵~取得该研究的完整 namespace:key 字符串，用于白名单比对
                String fullKey = r.getKey().toString();
                String rKey = r.getKey().getKey();

                // 喵~防御：swr_ 前缀是自定义研究自己注册的，已由 unregisterOldCustomResearches 处理
                // 这里只处理来自 addon 的非 swr_ 研究
                if (rKey.startsWith(RESEARCH_PREFIX)) {
                    continue;
                }

                // 喵~白名单检查：若该研究不在 CustomResearches.yml 的允许列表中，则将其移除
                if (!allowedKeys.contains(fullKey)) {
                    for (SlimefunItem item : new ArrayList<>(r.getAffectedItems())) {
                        // 喵~解绑物品与研究的关联，让物品变为无需研究即可使用
                        if (item != null && item.getResearch() == r) {
                            item.setResearch(null);
                        }
                    }
                    it.remove();
                    removed++;
                    plugin.getLogger().info("[CRM] INTERCEPT addon research not in CustomResearches.yml: " + fullKey);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[CRM] Failed to unregister non-custom research", e);
            }
        }
        return removed;
    }
    
    private static void loadAndRegister() {
        plugin.getLogger().info("[CRM] LOAD configFile=" + configFile.getAbsolutePath() + " exists=" + configFile.exists());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // 喵~检查自定义研究系统总开关，关闭时跳过所有处理
        if (!config.getBoolean("enabled", false)) {
            plugin.getLogger().info("[CRM] LOAD custom research system disabled");
            return;
        }

        // 喵~第一步：清理上一次由本管理器注册的 swr_ 前缀研究（避免重复注册）
        int removed = unregisterOldCustomResearches();
        plugin.getLogger().info("[CRM] LOAD removed " + removed + " old custom researches from registry");

        ConfigurationSection researchesSection = config.getConfigurationSection("researches");
        if (researchesSection == null) {
            plugin.getLogger().warning("[CRM] LOAD no researches section");
            // 喵~防御：researches 节点为空时，白名单为空集合，移除所有 addon 研究
            int intercepted = unregisterNonCustomResearches(new HashSet<>());
            plugin.getLogger().info("[CRM] LOAD intercepted " + intercepted + " addon researches (empty researches section)");
            return;
        }

        // 喵~第二步：收集 CustomResearches.yml 中配置的允许研究白名单（fullKey = namespace:key）
        // 仅收集 enabled=true 的研究 key，disabled 的研究不进入白名单（同样被拦截）
        Set<String> allowedResearchKeys = new HashSet<>();
        for (String fullKey : researchesSection.getKeys(false)) {
            ConfigurationSection sec = researchesSection.getConfigurationSection(fullKey);
            // 喵~防御：sec 为 null 说明该节点是标量而非子节点，跳过
            if (sec == null) continue;
            // 喵~只有显式 enabled=true（或未设置，默认 true）的研究才进入白名单
            if (sec.getBoolean("enabled", true)) {
                allowedResearchKeys.add(fullKey);
            }
        }
        plugin.getLogger().info("[CRM] LOAD allowedResearchKeys count=" + allowedResearchKeys.size());

        // 喵~第三步：拦截所有不在白名单的 addon 研究（核心修复点）
        // 当自定义研究启用且 CustomResearches.yml 中不含该研究时，addon 调用 register() 的结果将被清除
        int intercepted = unregisterNonCustomResearches(allowedResearchKeys);
        plugin.getLogger().info("[CRM] LOAD intercepted " + intercepted + " addon researches not in CustomResearches.yml");
        
        Map<String, ConfigurationSection> researchConfigs = new LinkedHashMap<>();
        for (String fullKey : researchesSection.getKeys(false)) {
            ConfigurationSection sec = researchesSection.getConfigurationSection(fullKey);
            if (sec != null) {
                researchConfigs.put(fullKey, sec);
            }
        }
        
        plugin.getLogger().info("[CRM] LOAD " + researchConfigs.size() + " research configs");
        
        for (Map.Entry<String, ConfigurationSection> entry : researchConfigs.entrySet()) {
            try {
                registerOne(entry.getKey(), entry.getValue(), researchConfigs);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[CRM] LOAD failed to register: " + entry.getKey(), e);
            }
        }
        
        plugin.getLogger().info("[CRM] LOAD registered " + researchConfigs.size() + " custom researches");
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
        plugin.getLogger().info("[CRM] DELETE " + fullKey + " configFile.exists=" + configFile.exists());
        if (!configFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        ConfigurationSection researchesSection = config.getConfigurationSection("researches");
        if (researchesSection == null) {
            plugin.getLogger().info("[CRM] DELETE " + fullKey + " researchesSection is null");
            return;
        }
        boolean existed = researchesSection.contains(fullKey);
        researchesSection.set(fullKey, null);
        plugin.getLogger().info("[CRM] DELETE " + fullKey + " existed=" + existed);
        
        int parentCleaned = 0;
        for (String otherKey : researchesSection.getKeys(false)) {
            ConfigurationSection other = researchesSection.getConfigurationSection(otherKey);
            if (other == null) continue;
            List<String> parents = other.getStringList("parents");
            if (parents.remove(fullKey)) {
                other.set("parents", parents);
                parentCleaned++;
            }
        }
        
        plugin.getLogger().info("[CRM] DELETE " + fullKey + " parentCleaned=" + parentCleaned);
        config.save(configFile);

        try {
            for (java.util.Iterator<Research> it = Slimefun.getRegistry().getResearches().iterator(); it.hasNext(); ) {
                Research r = it.next();
                if (r.getKey().toString().equals(fullKey)) {
                    for (SlimefunItem item : new ArrayList<>(r.getAffectedItems())) {
                        if (item != null && item.getResearch() == r) {
                            item.setResearch(null);
                        }
                    }
                    it.remove();
                    plugin.getLogger().info("[CRM] DELETE removed " + fullKey + " from registry");
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[CRM] DELETE failed to remove " + fullKey + " from registry", e);
        }
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
