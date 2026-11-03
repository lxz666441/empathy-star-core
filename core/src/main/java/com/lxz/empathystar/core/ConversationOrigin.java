package com.lxz.empathystar.core;

import java.util.Objects;

/** Host-owned opaque source identity. Advance revision when conversation data changes. */
public final class ConversationOrigin {
    public final String sourceId;
    public final long revision;

    public ConversationOrigin(String sourceId, long revision) {
        if (sourceId == null || sourceId.trim().isEmpty() || sourceId.length() > 200 || revision < 0)
            throw new IllegalArgumentException("INVALID_ORIGIN");
        this.sourceId = sourceId;
        this.revision = revision;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ConversationOrigin)) return false;
        ConversationOrigin value = (ConversationOrigin) other;
        return revision == value.revision && sourceId.equals(value.sourceId);
    }

    @Override public int hashCode() { return Objects.hash(sourceId, revision); }
}
