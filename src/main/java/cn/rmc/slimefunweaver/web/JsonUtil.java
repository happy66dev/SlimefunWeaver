// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy (k666kkk666k@163.com)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package cn.rmc.slimefunweaver.web;

import cn.rmc.slimefunweaver.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JsonUtil {

    private JsonUtil() {}

    public static String categoriesToJson(List<CustomCategory> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"categories\":[");
        Set<String> visitedKeys = new HashSet<>();
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) sb.append(',');
            appendCategory(sb, categories.get(i), visitedKeys);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendCategory(StringBuilder sb, CustomCategory cat, Set<String> visitedKeys) {
        String key = cat.getKey();
        if (!visitedKeys.add(key)) {
            sb.append("{\"key\":\"");
            sb.append(escape(key));
            sb.append("\",\"_cycle\":true}");
            return;
        }
        IconSource iconSource = cat.getIconSource();
        String iconTypeName = iconSource != null ? iconSource.getType().name() : "VANILLA";
        String iconId = iconSource != null ? iconSource.getId() : "BOOK";
        sb.append('{');
        appendString(sb, "key", key);
        sb.append(',');
        appendString(sb, "display", cat.getDisplay());
        sb.append(',');
        sb.append("\"icon\":{\"type\":\"");
        sb.append(escape(iconTypeName));
        sb.append("\",\"id\":\"");
        sb.append(escape(iconId));
        sb.append("\"},");
        sb.append("\"glow\":").append(cat.isGlow()).append(',');
        appendStrings(sb, "lore", cat.getLore());
        sb.append(',');
        sb.append("\"page\":").append(cat.getPage()).append(',');
        sb.append("\"slot\":").append(cat.getSlot()).append(',');
        sb.append("\"children\":[");
        boolean firstChild = true;
        List<GuideTreeNode> children = cat.getChildren();
        for (GuideTreeNode child : children) {
            if (child.getType() == TreeNodeType.CATEGORY) {
                if (!firstChild) sb.append(',');
                appendCategory(sb, (CustomCategory) child, visitedKeys);
                firstChild = false;
            }
        }
        sb.append("],\"items\":[");
        boolean first = true;
        for (GuideTreeNode child : children) {
            if (child.getType() == TreeNodeType.ITEM) {
                if (!first) sb.append(',');
                appendItemEntry(sb, (CustomItemEntry) child);
                first = false;
            } else if (child.getType() == TreeNodeType.PLACEHOLDER) {
                if (!first) sb.append(',');
                appendPlaceholderEntry(sb, (CustomPlaceholderEntry) child);
                first = false;
            } else if (child.getType() == TreeNodeType.REFERENCE) {
                if (!first) sb.append(',');
                appendReferenceEntry(sb, (CustomReferenceEntry) child);
                first = false;
            }
        }
        sb.append("]}");
    }

    private static void appendItemEntry(StringBuilder sb, CustomItemEntry entry) {
        sb.append("{\"type\":\"ITEM\",");
        appendString(sb, "id", entry.getSlimefunId());
        sb.append(',');
        appendString(sb, "display", entry.getDisplay());
        sb.append(',');
        sb.append("\"page\":").append(entry.getPage()).append(',');
        sb.append("\"slot\":").append(entry.getSlot()).append('}');
    }

    private static void appendPlaceholderEntry(StringBuilder sb, CustomPlaceholderEntry entry) {
        IconSource iconSource = entry.getIconSource();
        String iconTypeName = iconSource != null ? iconSource.getType().name() : "VANILLA";
        String iconId = iconSource != null ? iconSource.getId() : "BOOK";
        sb.append("{\"type\":\"PLACEHOLDER\",");
        appendString(sb, "display", entry.getDisplay());
        sb.append(',');
        sb.append("\"icon\":{\"type\":\"");
        sb.append(escape(iconTypeName));
        sb.append("\",\"id\":\"");
        sb.append(escape(iconId));
        sb.append("\"},");
        sb.append("\"glow\":").append(entry.isGlow()).append(',');
        appendStrings(sb, "lore", entry.getLore());
        sb.append(',');
        sb.append("\"page\":").append(entry.getPage()).append(',');
        sb.append("\"slot\":").append(entry.getSlot()).append('}');
    }

    private static void appendReferenceEntry(StringBuilder sb, CustomReferenceEntry entry) {
        IconSource iconSource = entry.getIconSource();
        String iconTypeName = iconSource != null ? iconSource.getType().name() : "VANILLA";
        String iconId = iconSource != null ? iconSource.getId() : "ARROW";
        sb.append("{\"type\":\"REFERENCE\",");
        sb.append("\"ref\":\"");
        sb.append(escape(entry.getTargetCategoryKey()));
        sb.append("\",");
        sb.append("\"mode\":\"");
        sb.append(escape(entry.getMode()));
        sb.append("\",");
        appendString(sb, "display", entry.getDisplay());
        sb.append(',');
        sb.append("\"icon\":{\"type\":\"");
        sb.append(escape(iconTypeName));
        sb.append("\",\"id\":\"");
        sb.append(escape(iconId));
        sb.append("\"},");
        sb.append("\"glow\":").append(entry.isGlow()).append(',');
        appendStrings(sb, "lore", entry.getLore());
        sb.append(',');
        sb.append("\"page\":").append(entry.getPage()).append(',');
        sb.append("\"slot\":").append(entry.getSlot()).append('}');
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"");
        sb.append(escape(value));
        sb.append('"');
    }

    private static void appendStrings(StringBuilder sb, String key, List<String> values) {
        sb.append('"').append(key).append("\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        sb.append(']');
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        out.append("\\u00").append(String.format("%02x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
