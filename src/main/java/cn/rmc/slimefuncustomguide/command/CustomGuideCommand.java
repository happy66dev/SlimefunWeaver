package cn.rmc.slimefuncustomguide.command;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CustomGuideCommand implements CommandExecutor, TabCompleter {

    private final CustomGuidePlugin plugin;

    public CustomGuideCommand(CustomGuidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/scg export §7- 导出原版分类结构");
            sender.sendMessage("§e/scg export flat §7- 平铺导出");
            sender.sendMessage("§e/scg reload §7- 重新加载 categories.yml");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            if (!sender.hasPermission("slimefuncustomguide.reload")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            plugin.reloadCategories();
            sender.sendMessage("§acategories.yml 已重新加载");
            return true;
        }

        if (sub.equals("export")) {
            if (!sender.hasPermission("slimefuncustomguide.export")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            boolean flat = args.length > 1 && args[1].equalsIgnoreCase("flat");
            exportCategories(sender, flat);
            return true;
        }

        return false;
    }

    private void exportCategories(CommandSender sender, boolean flat) {
        List<ItemGroup> itemGroups = Slimefun.getRegistry().getAllItemGroups();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("categories");
        int slotCounter = 10;
        int pageCounter = 1;

        for (ItemGroup group : itemGroups) {
            String key = group.getUnlocalizedName().replaceAll("[&§]", "");
            if (key == null || key.isEmpty()) continue;
            String display = "&f" + group.getDisplayName(null);
            String iconId = "CHEST";
            if (group.getItem(null) != null && group.getItem(null).getType() != null) {
                iconId = group.getItem(null).getType().name();
            }

            ConfigurationSection groupSection = root.createSection(key);
            groupSection.set("display", display);
            ConfigurationSection iconSection = groupSection.createSection("icon");
            iconSection.set("type", "VANILLA");
            iconSection.set("id", iconId);
            groupSection.set("page", pageCounter);
            groupSection.set("slot", slotCounter);
            slotCounter++;
            if (slotCounter > 45) {
                slotCounter = 10;
                pageCounter++;
            }

            List<SlimefunItem> items = group.getItems();
            if (!items.isEmpty()) {
                List<Map<String, Object>> itemList = new ArrayList<>();
                int itemSlot = 10;
                int itemPage = 1;
                for (SlimefunItem sfItem : items) {
                    Map<String, Object> itemMap = new LinkedHashMap<>();
                    itemMap.put("id", sfItem.getId());
                    itemMap.put("page", itemPage);
                    itemMap.put("slot", itemSlot);
                    itemList.add(itemMap);
                    itemSlot++;
                    if (itemSlot > 45) {
                        itemSlot = 10;
                        itemPage++;
                    }
                }
                groupSection.set("items", itemList);

                List<String> lore = new ArrayList<>();
                lore.add("&7共 {total_items_count} 个物品");
                groupSection.set("lore", lore);
            }
        }

        File file = new File(plugin.getDataFolder(), "categories.yml");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(yaml.saveToString());
            writer.flush();
            sender.sendMessage("§a导出完成: " + file.getAbsolutePath());
            plugin.reloadCategories();
        } catch (Exception e) {
            sender.sendMessage("§c导出失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("export", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("export")) {
            return Collections.singletonList("flat");
        }
        return Collections.emptyList();
    }
}
