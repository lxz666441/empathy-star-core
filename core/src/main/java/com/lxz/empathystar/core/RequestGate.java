package com.lxz.empathystar.core;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Recheck host consent and source identity before upload and after receiving a response. */
public final class RequestGate {
    private final ConversationOrigin expected;
    private final Supplier<ConversationOrigin> current;
    private final BooleanSupplier consent;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;

    public RequestGate(ConversationOrigin expected, Supplier<ConversationOrigin> current, BooleanSupplier consent) {
        this.expected = Objects.requireNonNull(expected);
        this.current = Objects.requireNonNull(current);
        this.consent = Objects.requireNonNull(consent);
    }

    public ConversationOrigin origin() { return expected; }

    public synchronized void check(HttpURLConnection connection) throws ChatModelClient.Failure {
        if (connection != null) active = connection;
        String error = cancelled || Thread.currentThread().isInterrupted() ? "REQUEST_CANCELLED"
            : !consent.getAsBoolean() ? "UPLOAD_CONSENT_REQUIRED"
            : !expected.equals(current.get()) ? "STALE_SOURCE" : null;
        if (error != null) {
            if (active != null) active.disconnect();
            throw new ChatModelClient.Failure(error);
        }
    }

    /** Best effort only. Bytes already delivered to a service cannot be recalled. */
    public synchronized void cancel() {
        cancelled = true;
        if (active != null) active.disconnect();
    }

    synchronized void release(HttpURLConnection connection) { if (active == connection) active = null; }
}
