// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy (k666kkk666k@163.com)
package cn.rmc.slimefunweaver.util;

import org.bukkit.ChatColor;

/**
 * 颜色代码处理工具类
 */
public class ColorUtil {
    
    /**
     * 移除文本中的所有颜色代码（同时支持 § 和 & 两种格式）
     * 
     * @param text 原始文本
     * @return 去除颜色代码后的纯文本
     */
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 先将 & 转换为 §，然后统一用 ChatColor.stripColor 处理
        String normalized = text.replace('&', '§');
        return ChatColor.stripColor(normalized);
    }
}
