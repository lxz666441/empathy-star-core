package com.lxz.empathystar.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Business input policy only: no application identifiers, geometry or UI automation. */
public final class BusinessPolicy {
    private BusinessPolicy() { }

    public static boolean isSecureModelEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) return false;
        try {
            URL url = new URL(endpoint);
            return "https".equalsIgnoreCase(url.getProtocol()) && !url.getHost().isEmpty()
                && url.getUserInfo() == null && url.getRef() == null;
        } catch (MalformedURLException invalid) { return false; }
    }

    /** Callers pass chronological messages and the selected occurrence's index, not string matching. */
    public static List<String> precedingContext(List<String> messages, int selectedIndex) {
        if (messages == null || selectedIndex < 0 || selectedIndex >= messages.size())
            throw new IllegalArgumentException("INVALID_SELECTED_INDEX");
        return Collections.unmodifiableList(new ArrayList<>(messages.subList(Math.max(0, selectedIndex - 8), selectedIndex)));
    }

    public static String analysisUserPrompt(String target, List<String> precedingMessages) {
        String context = precedingMessages == null || precedingMessages.isEmpty() ? "（无）" : String.join("\n", precedingMessages);
        return "所选消息之前的可见文字（可能包含双方消息，顺序从早到晚）：\n"
            + context + "\n\n请只分析这条所选消息：" + target;
    }
}
