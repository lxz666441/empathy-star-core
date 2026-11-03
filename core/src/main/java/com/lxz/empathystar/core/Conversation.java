package com.lxz.empathystar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable text input. The host owns reading authorization and provenance. */
public final class Conversation {
    public enum Side {
        UNKNOWN(0), INCOMING(1), OUTGOING(2);
        public final int code;
        Side(int code) { this.code = code; }
    }

    public static final class Message {
        public final int id;
        public final String text;
        public final Side side;
        public final String speakerName;

        public Message(int id, String text, Side side) { this(id, text, side, ""); }
        public Message(int id, String text, Side side, String speakerName) {
            if (id <= 0 || text == null || side == null) throw new IllegalArgumentException("INVALID_MESSAGE");
            this.id = id;
            this.text = text;
            this.side = side;
            String name = speakerName == null ? "" : speakerName.trim();
            this.speakerName = name.length() > 80 || ChatContentPolicy.sensitive(name) ? "" : name;
        }
    }

    public final ConversationOrigin origin;
    public final List<Message> messages;
    public final boolean group;

    public Conversation(ConversationOrigin origin, List<Message> messages, boolean group) {
        if (origin == null || messages == null) throw new IllegalArgumentException("INVALID_CONVERSATION");
        Set<Integer> ids = new HashSet<>();
        for (Message message : messages)
            if (message == null || !ids.add(message.id)) throw new IllegalArgumentException("DUPLICATE_MESSAGE_ID");
        this.origin = origin;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.group = group;
    }
}
