package cn.rmc.slimefuncustomguide.command;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

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

        Map<NestedItemGroup, List<SubItemGroup>> nestedMap = new LinkedHashMap<>();
        if (flat) {
            for (ItemGroup group : itemGroups) {
                if (group instanceof SubItemGroup) {
                    SubItemGroup sub = (SubItemGroup) group;
                    NestedItemGroup parent = sub.getParent();
                    nestedMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(sub);
                }
            }
        }

        for (ItemGroup group : itemGroups) {
            if (flat && group instanceof SubItemGroup) {
                continue;
            }

            String key = group.getUnlocalizedName().replaceAll("[&§][0-9a-fA-Fk-oK-OrR]", "");
            if (key == null || key.isEmpty()) continue;
            String display = "&f" + group.getDisplayName(null);

            ItemStack repItem = group.getItem(null);
            String iconId = "CHEST";
            if (repItem != null && repItem.getType() != null) {
                iconId = repItem.getType().name();
            }

            ConfigurationSection groupSection = root.createSection(key);
            groupSection.set("display", display);
            ConfigurationSection iconSection = groupSection.createSection("icon");

            SlimefunItem sfIconItem = SlimefunItem.getByItem(repItem);
            if (sfIconItem != null) {
                iconSection.set("type", "SLIMEFUN");
                iconSection.set("id", sfIconItem.getId());
            } else {
                iconSection.set("type", "VANILLA");
                iconSection.set("id", iconId);
            }

            groupSection.set("page", pageCounter);
            groupSection.set("slot", slotCounter);
            slotCounter++;
            if (slotCounter > 45) {
                slotCounter = 10;
                pageCounter++;
            }

            List<SlimefunItem> items = new ArrayList<>();
            if (flat && group instanceof NestedItemGroup) {
                List<SubItemGroup> subGroups = nestedMap.getOrDefault(group, Collections.emptyList());
                for (SubItemGroup sub : subGroups) {
                    items.addAll(sub.getItems());
                }
            } else if (!(group instanceof FlexItemGroup)) {
                items = group.getItems();
            }

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
