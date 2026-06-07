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
package cn.rmc.slimefuncustomguide.web;

import cn.rmc.slimefuncustomguide.CustomGuidePlugin;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class WebSecurity {

    static final int MAX_BODY_BYTES = 1024 * 1024;

    private WebSecurity() {}

    static String readBody(HttpExchange exchange) throws IOException, BodyTooLargeException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null) {
            try {
                if (Long.parseLong(length) > MAX_BODY_BYTES) throw new BodyTooLargeException();
            } catch (NumberFormatException ignored) {}
        }
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (baos.size() + n > MAX_BODY_BYTES) throw new BodyTooLargeException();
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static boolean isAccessAllowed(CustomGuidePlugin plugin, HttpExchange exchange) {
        String token = plugin.getConfig().getString("web-editor.token", "");
        if (token != null && !token.isEmpty()) {
            String header = exchange.getRequestHeaders().getFirst("X-SlimefunCustomGuide-Token");
            return token.equals(header) || token.equals(readAuthCookie(exchange));
        }
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return address != null && address.isLoopbackAddress();
    }

    static boolean isLoginValid(CustomGuidePlugin plugin, String body) {
        String token = plugin.getConfig().getString("web-editor.token", "");
        if (token == null || token.isEmpty()) return false;
        String prefix = "token=";
        if (body == null || !body.startsWith(prefix)) return false;
        String submitted = body.substring(prefix.length()).replace("+", " ");
        try {
            submitted = java.net.URLDecoder.decode(submitted, "UTF-8");
        } catch (Exception ignored) {}
        return token.equals(submitted);
    }

    static void setAuthCookie(HttpExchange exchange, String token) {
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseHeaders().add("Set-Cookie", "SCG_AUTH=" + encoded + "; Path=/; HttpOnly; SameSite=Strict");
    }

    static boolean isWriteAllowed(CustomGuidePlugin plugin, HttpExchange exchange) {
        String token = plugin.getConfig().getString("web-editor.token", "");
        return token != null && !token.isEmpty() && isAccessAllowed(plugin, exchange);
    }

    public static boolean isSafeBind(String bind) {
        return bind == null || bind.equals("127.0.0.1") || bind.equals("localhost") || bind.equals("::1");
    }

    private static String readAuthCookie(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("SCG_AUTH=")) {
                try {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(trimmed.substring("SCG_AUTH=".length()));
                    return new String(decoded, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    static class BodyTooLargeException extends Exception {}
}
