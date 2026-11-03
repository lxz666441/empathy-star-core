package com.lxz.empathystar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One host-supplied visible frame. Explicit selection is required before summary upload. */
public final class ConversationSelection {
    public static final int MAX_MESSAGES = 30, MAX_CHARACTERS = 12000;

    public static final class Entry {
        public final int id, side;
        public final String text, speakerName;
        private Entry(Conversation.Message message) {
            id = message.id; text = message.text; side = message.side.code; speakerName = message.speakerName;
        }
        public String speaker() {
            if (!speakerName.isEmpty()) return "显示名：" + speakerName + "（非唯一身份）";
            return side == 1 ? "对方（未识别姓名）" : side == 2 ? "自己" : "方向/说话人不明";
        }
    }

    public final ConversationOrigin origin;
    public final List<Entry> entries;
    public final int filteredCount;
    public final boolean group;
    private final boolean explicitSelection;

    private ConversationSelection(ConversationOrigin origin, List<Entry> entries, int filteredCount,
                                  boolean group, boolean explicitSelection) {
        this.origin = origin;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.filteredCount = filteredCount; this.group = group; this.explicitSelection = explicitSelection;
    }

    public static ConversationSelection from(Conversation conversation) {
        if (conversation == null) throw new IllegalArgumentException("SUMMARY_NO_READABLE_MESSAGES");
        List<Entry> safe = new ArrayList<>(); int filtered = 0;
        for (Conversation.Message message : conversation.messages) {
            if (message.text.trim().isEmpty() || ChatContentPolicy.sensitive(message.text)) { filtered++; continue; }
            safe.add(new Entry(message));
        }
        return new ConversationSelection(conversation.origin, safe, filtered, conversation.group, false);
    }

    public ConversationSelection select(Set<Integer> selected) {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("SUMMARY_EMPTY_SELECTION");
        Set<Integer> remaining = new HashSet<>(selected);
        List<Entry> chosen = new ArrayList<>(); int characters = 0;
        for (Entry entry : entries) if (remaining.remove(entry.id)) {
            if (ChatContentPolicy.sensitive(entry.text)) throw new IllegalArgumentException("SUMMARY_SENSITIVE_CONTENT");
            chosen.add(entry); characters += entry.text.length();
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("SUMMARY_UNKNOWN_SELECTION");
        if (chosen.size() > MAX_MESSAGES || characters > MAX_CHARACTERS)
            throw new IllegalArgumentException("SUMMARY_SELECTION_TOO_LARGE");
        return new ConversationSelection(origin, chosen, filteredCount, group, true);
    }

    public boolean isExplicitSelection() { return explicitSelection; }
    public boolean belongsTo(ConversationOrigin current) { return origin.equals(current); }
    public int characters() { int count = 0; for (Entry entry : entries) count += entry.text.length(); return count; }
    public Entry find(int id) { for (Entry entry : entries) if (entry.id == id) return entry; return null; }
}
