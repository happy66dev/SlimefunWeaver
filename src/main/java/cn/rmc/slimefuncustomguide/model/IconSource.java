package cn.rmc.slimefuncustomguide.model;

public class IconSource {
    private final IconType type;
    private final String id;

    public IconSource(IconType type, String id) {
        this.type = type;
        this.id = id;
    }

    public IconType getType() { return type; }
    public String getId() { return id; }
}
