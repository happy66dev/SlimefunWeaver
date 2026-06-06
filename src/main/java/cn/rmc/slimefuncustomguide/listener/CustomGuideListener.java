package cn.rmc.slimefuncustomguide.listener;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import cn.rmc.slimefuncustomguide.guide.CustomGuideHistory;
import cn.rmc.slimefuncustomguide.guide.CustomGuideRenderer;
import cn.rmc.slimefuncustomguide.settings.CustomGuideSettings;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunGuideOpenEvent;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class CustomGuideListener implements Listener {

    private static final NamespacedKey MODE_KEY = new NamespacedKey("slimefuncustomguide", "custom_mode");
    private static final String MODE_CUSTOM = "custom";

    private final CustomGuidePlugin plugin;
    private final CustomGuideRenderer renderer;
    private final Map<Player, CustomGuideHistory> histories = new ConcurrentHashMap<>();

    public CustomGuideListener(CustomGuidePlugin plugin) {
        this.plugin = plugin;
        this.renderer = new CustomGuideRenderer(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGuideOpen(SlimefunGuideOpenEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isCustomGuideEnabled()) return;

        ItemStack guide = event.getGuide();

        if (player.isSneaking()) {
            event.setCancelled(true);
            CustomGuideSettings.openSettings(player, guide);
            return;
        }

        if (!isCustomMode(guide)) return;

        event.setCancelled(true);

        PlayerProfile.get(player, profile -> {
            CustomGuideHistory history = histories.computeIfAbsent(player, p -> new CustomGuideHistory());
            SlimefunGuideMode mode = SlimefunUtils.isItemSimilar(
                    guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false)
                    ? SlimefunGuideMode.CHEAT_MODE
                    : SlimefunGuideMode.SURVIVAL_MODE;

            history.clear();
            history.setMainMenuPage(1);
            renderer.openMainMenu(player, history, mode, 1);
        });
    }

    public static boolean isCustomMode(ItemStack guide) {
        if (guide == null || !guide.hasItemMeta()) return false;
        return MODE_CUSTOM.equals(guide.getItemMeta().getPersistentDataContainer().get(MODE_KEY, PersistentDataType.STRING));
    }

    public static void setCustomMode(ItemStack guide) {
        if (guide == null) return;
        ItemMeta meta = guide.hasItemMeta() ? guide.getItemMeta() : org.bukkit.Bukkit.getItemFactory().getItemMeta(guide.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, MODE_CUSTOM);
        guide.setItemMeta(meta);
    }

    public static void clearCustomMode(ItemStack guide) {
        if (guide == null || !guide.hasItemMeta()) return;
        ItemMeta meta = guide.getItemMeta();
        meta.getPersistentDataContainer().remove(MODE_KEY);
        guide.setItemMeta(meta);
    }

    public void removeHistory(Player player) { histories.remove(player); }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        CustomGuidePlugin.ItemDetailReturn ret = plugin.getItemDetailReturns().remove(e.getPlayer());
        if (ret == null) return;
        Player p = (Player) e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            CustomGuideHistory history = histories.computeIfAbsent(p, k -> new CustomGuideHistory());
            if (ret.category != null) {
                renderer.openMenu(p, history, ret.mode, ret.category, ret.page);
            } else {
                renderer.openMainMenu(p, history, ret.mode, ret.page);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        histories.remove(e.getPlayer());
        plugin.getItemDetailReturns().remove(e.getPlayer());
    }
}
