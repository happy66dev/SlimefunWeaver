package cn.rmc.slimefuncustomguide.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomPlaceholderEntry implements GuideTreeNode {
    private final IconSource icon;
    private final String display;
    private final List<String> lore;
    private final int page;
    private final int slot;

    public CustomPlaceholderEntry(IconSource icon, String display,
                                  List<String> lore, int page, int slot) {
        this.icon = icon;
        this.display = display != null ? display : "";
        this.lore = lore != null ? new ArrayList<>(lore) : Collections.<String>emptyList();
        this.page = page;
        this.slot = slot;
    }

    public IconSource getIconSource() { return icon; }

    @Override public TreeNodeType getType() { return TreeNodeType.PLACEHOLDER; }
    @Override public String getDisplay() { return display; }

    @Override
    public ItemStack getIcon(Player player) { return null; }

    @Override public List<String> getLore() { return Collections.unmodifiableList(lore); }
    @Override public int getPage() { return page; }
    @Override public int getSlot() { return slot; }
}
