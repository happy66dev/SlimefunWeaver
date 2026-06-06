package cn.rmc.slimefuncustomguide.util;

import cn.rmc.slimefuncustomguide.model.IconSource;
import cn.rmc.slimefuncustomguide.model.IconType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IconParser {

    private IconParser() {}

    public static Optional<ItemStack> parse(IconSource source, Logger logger) {
        if (source == null) return Optional.empty();

        switch (source.getType()) {
            case VANILLA:  return parseVanilla(source.getId(), logger);
            case SLIMEFUN: return parseSlimefun(source.getId(), logger);
            case HEAD:     return parseHead(source.getId(), logger);
            default:       return Optional.empty();
        }
    }

    public static Optional<ItemStack> parse(IconSource source, Logger logger, boolean glow) {
        Optional<ItemStack> opt = parse(source, logger);
        if (glow && opt.isPresent()) {
            ItemStack item = opt.get();
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return opt;
    }

    private static Optional<ItemStack> parseVanilla(String id, Logger logger) {
        if (id == null || id.isEmpty()) {
            logger.warning("VANILLA icon ID is empty");
            return Optional.empty();
        }
        try {
            return Optional.of(new ItemStack(Material.valueOf(id.toUpperCase())));
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Material not found: {0}", id);
            return Optional.empty();
        }
    }

    private static Optional<ItemStack> parseSlimefun(String id, Logger logger) {
        if (id == null || id.isEmpty()) {
            logger.warning("SLIMEFUN icon ID is empty");
            return Optional.empty();
        }
        NamespacedKey key = id.contains(":")
                ? NamespacedKey.fromString(id)
                : new NamespacedKey(Slimefun.instance(), id);
        if (key == null) {
            logger.log(Level.WARNING, "Invalid Slimefun ID: {0}", id);
            return Optional.empty();
        }
        SlimefunItem sfItem = SlimefunItem.getById(key.toString());
        if (sfItem == null) {
            sfItem = SlimefunItem.getById(key.getKey());
        }
        if (sfItem != null) {
            return Optional.of(sfItem.getItem().clone());
        }
        logger.log(Level.WARNING, "Slimefun item not found: {0}", id);
        return Optional.empty();
    }

    @SuppressWarnings("deprecation")
    private static Optional<ItemStack> parseHead(String textureId, Logger logger) {
        if (textureId == null || textureId.isEmpty()) {
            logger.warning("HEAD texture is empty");
            return Optional.empty();
        }
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            return Optional.of(head);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to create head: " + textureId, e);
            return Optional.empty();
        }
    }
}
