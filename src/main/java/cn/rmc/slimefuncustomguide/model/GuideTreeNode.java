package cn.rmc.slimefuncustomguide.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public interface GuideTreeNode {
    TreeNodeType getType();
    String getDisplay();
    ItemStack getIcon(Player player);
    List<String> getLore();
    int getPage();
    int getSlot();
}
