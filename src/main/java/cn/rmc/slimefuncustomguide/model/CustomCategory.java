package cn.rmc.slimefuncustomguide.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomCategory implements GuideTreeNode {
    private final String key;
    private final String display;
    private final IconSource icon;
    private final List<String> lore;
    private final int page;
    private final int slot;
    private final List<GuideTreeNode> children;

    private int childrenCount;
    private int directItemsCount;
    private int totalItemsCount;

    public CustomCategory(String key, String display, IconSource icon,
                          List<String> lore, int page, int slot) {
        this.key = key;
        this.display = display != null ? display : key;
        this.icon = icon;
        this.lore = lore != null ? new ArrayList<>(lore) : Collections.<String>emptyList();
        this.page = page;
        this.slot = slot;
        this.children = new ArrayList<>();
    }

    public String getKey() { return key; }

    @Override public TreeNodeType getType() { return TreeNodeType.CATEGORY; }
    @Override public String getDisplay() { return display; }

    @Override
    public ItemStack getIcon(Player player) { return null; }

    @Override public List<String> getLore() { return Collections.unmodifiableList(lore); }
    @Override public int getPage() { return page; }
    @Override public int getSlot() { return slot; }

    public IconSource getIconSource() { return icon; }
    public void addChild(GuideTreeNode child) { children.add(child); }
    public List<GuideTreeNode> getChildren() { return Collections.unmodifiableList(children); }

    public int getChildrenCount() { return childrenCount; }
    public void setChildrenCount(int childrenCount) { this.childrenCount = childrenCount; }
    public int getDirectItemsCount() { return directItemsCount; }
    public void setDirectItemsCount(int directItemsCount) { this.directItemsCount = directItemsCount; }
    public int getTotalItemsCount() { return totalItemsCount; }
    public void setTotalItemsCount(int totalItemsCount) { this.totalItemsCount = totalItemsCount; }
}
