package cn.rmc.slimefuncustomguide.model;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.List;

public class CustomItemEntry implements GuideTreeNode {
    private final String slimefunId;
    private final SlimefunItem sfItem;
    private final int page;
    private final int slot;

    public CustomItemEntry(String slimefunId, SlimefunItem sfItem, int page, int slot) {
        this.slimefunId = slimefunId;
        this.sfItem = sfItem;
        this.page = page;
        this.slot = slot;
    }

    public String getSlimefunId() { return slimefunId; }
    public SlimefunItem getSlimefunItem() { return sfItem; }

    @Override public TreeNodeType getType() { return TreeNodeType.ITEM; }
    @Override public String getDisplay() { return sfItem.getItemName(); }

    @Override
    public ItemStack getIcon(Player player) { return sfItem.getItem().clone(); }

    @Override public List<String> getLore() { return Collections.emptyList(); }
    @Override public int getPage() { return page; }
    @Override public int getSlot() { return slot; }
}
