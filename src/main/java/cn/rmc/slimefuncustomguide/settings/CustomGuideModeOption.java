package cn.rmc.slimefuncustomguide.settings;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideOption;
import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.listener.CustomGuideListener;
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
        return new NamespacedKey("slimefuncustomguide", "custom_guide_mode");
    }

    @Nonnull
    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        boolean isCustom = CustomGuideListener.isCustomMode(guide);
        ItemStack item = new ItemStack(isCustom ? Material.COMMAND_BLOCK : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "\u6307\u5357\u5206\u7c7b\u6837\u5f0f: " + ChatColor.YELLOW + (isCustom ? "\u81ea\u5b9a\u4e49" : "\u539f\u7248"));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add((isCustom ? ChatColor.GREEN : ChatColor.GRAY) + "\u81ea\u5b9a\u4e49\u6a21\u5f0f");
        lore.add((!isCustom ? ChatColor.GREEN : ChatColor.GRAY) + "\u539f\u7248\u6a21\u5f0f");
        lore.add("");
        lore.add(ChatColor.GRAY + "\u21E8 " + ChatColor.YELLOW + "\u5355\u51fb\u5207\u6362\u5206\u7c7b\u6837\u5f0f");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return Optional.of(item);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onClick(Player p, ItemStack guide) {
        if (CustomGuideListener.isCustomMode(guide)) {
            CustomGuideListener.clearCustomMode(guide);
        } else {
            CustomGuideListener.setCustomMode(guide);
        }
        CustomGuideSettings.openSettings(p, guide);
    }

    @Nonnull
    @Override
    public Optional<Boolean> getSelectedOption(Player p, ItemStack guide) {
        return Optional.of(CustomGuideListener.isCustomMode(guide));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void setSelectedOption(Player p, ItemStack guide, Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            CustomGuideListener.setCustomMode(guide);
        } else {
            CustomGuideListener.clearCustomMode(guide);
        }
    }
}
