package com.lxz.empathystar.core;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** In-memory HTTP doubles only. No DNS, socket or real service is used. */
public final class ModelTransportTest {
    private static final String ENDPOINT = "https://example.invalid/chat/completions";
    private final ConversationOrigin origin = new ConversationOrigin("synthetic-frame", 1);
    private RequestGate gate() { return new RequestGate(origin, () -> origin, () -> true); }
    private ConversationSelection selection() {
        return ConversationSelection.from(new Conversation(origin, Arrays.asList(
            new Conversation.Message(4, "synthetic first", Conversation.Side.INCOMING),
            new Conversation.Message(8, "synthetic second", Conversation.Side.OUTGOING)), false));
    }
    private static String envelope(String content, String finish) {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", finish)
            .put("message", new JSONObject().put("content", content)))).toString();
    }
    private static final class MemoryConnection extends HttpURLConnection {
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        String response; int status = 200; boolean disconnected;
        Runnable beforeResponse = () -> { };
        MemoryConnection(String content) throws Exception { super(new URL(ENDPOINT)); response = envelope(content, "stop"); }
        @Override public OutputStream getOutputStream() { return request; }
        @Override public int getResponseCode() { beforeResponse.run(); return status; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
    @Test public void validAnalysisUsesOneRequestAndCleansUpConnection() throws Exception {
        MemoryConnection connection = new MemoryConnection(BusinessContractTest.analysisJson());
        AtomicInteger calls = new AtomicInteger();
        ChatModelClient client = new ChatModelClient.Https(endpoint -> { calls.incrementAndGet(); return connection; });
        assertEquals(3, client.analyze(ENDPOINT, "synthetic-model", "not-a-real-credential", "RAG", Collections.emptyList(), gate()).replies.size());
        assertEquals(1, calls.get()); assertTrue(connection.disconnected);
        assertFalse(connection.getInstanceFollowRedirects());
        JSONObject payload = new JSONObject(connection.request.toString("UTF-8"));
        assertEquals("synthetic-model", payload.getString("model"));
        assertEquals(2, payload.getJSONArray("messages").length());
    }
    @Test public void deniedConsentAndUnsafeInputNeverOpenAConnection() {
        AtomicInteger calls = new AtomicInteger();
        ChatModelClient client = new ChatModelClient.Https(endpoint -> { calls.incrementAndGet(); throw new AssertionError("Must not open"); });
        assertThrows(ChatModelClient.Failure.class, () -> client.analyze(ENDPOINT, "model", "synthetic", "RAG", null,
            new RequestGate(origin, () -> origin, () -> false)));
        assertThrows(IllegalArgumentException.class, () -> client.analyze(ENDPOINT, "model", "synthetic", "sk-" + "x".repeat(40), null, gate()));
        assertThrows(IllegalArgumentException.class, () -> client.analyze("http://example.invalid", "model", "synthetic", "RAG", null, gate()));
        assertEquals(0, calls.get());
    }
    @Test public void unselectedFramesAndWrongSourcesCannotBeSummarized() {
        AtomicInteger calls = new AtomicInteger();
        ChatModelClient client = new ChatModelClient.Https(endpoint -> { calls.incrementAndGet(); throw new AssertionError("Must not open"); });
        assertThrows(ChatModelClient.Failure.class, () -> client.summarize(ENDPOINT, "model", "synthetic", selection(), gate()));
        ConversationSelection selected = selection().select(Collections.singleton(4));
        ConversationOrigin other = new ConversationOrigin("other", 1);
        assertThrows(ChatModelClient.Failure.class, () -> client.summarize(ENDPOINT, "model", "synthetic", selected,
            new RequestGate(other, () -> other, () -> true)));
        assertEquals(0, calls.get());
    }
    @Test public void summaryUploadsOnlyChosenMessagesAndValidatesReturnedIds() throws Exception {
        MemoryConnection connection = new MemoryConnection(BusinessContractTest.summaryJson(8));
        ChatModelClient client = new ChatModelClient.Https(endpoint -> connection);
        ConversationSelection selected = selection().select(Collections.singleton(8));
        assertEquals(1, client.summarize(ENDPOINT, "model", "synthetic", selected, gate()).items.size());
        JSONObject payload = new JSONObject(connection.request.toString("UTF-8"));
        String user = payload.getJSONArray("messages").getJSONObject(1).getString("content");
        assertTrue(user.contains("synthetic second")); assertFalse(user.contains("synthetic first"));
    }
    @Test public void staleResponsesAreDiscardedAfterUpload() throws Exception {
        MemoryConnection connection = new MemoryConnection(BusinessContractTest.analysisJson());
        AtomicReference<ConversationOrigin> current = new AtomicReference<>(origin);
        connection.beforeResponse = () -> current.set(new ConversationOrigin("changed", 2));
        ChatModelClient client = new ChatModelClient.Https(endpoint -> connection);
        ChatModelClient.Failure error = assertThrows(ChatModelClient.Failure.class, () -> client.analyze(ENDPOINT,
            "model", "synthetic", "RAG", null, new RequestGate(origin, current::get, () -> true)));
        assertEquals("STALE_SOURCE", error.code); assertTrue(connection.disconnected);
    }
    @Test public void truncatedMalformedAndHttpErrorsHaveFixedCodesWithoutRetry() throws Exception {
        for (String scenario : Arrays.asList("truncated", "malformed", "http")) {
            MemoryConnection connection = new MemoryConnection(BusinessContractTest.analysisJson());
            if (scenario.equals("truncated")) connection.response = envelope("partial", "length");
            if (scenario.equals("malformed")) connection.response = "not json";
            if (scenario.equals("http")) connection.status = 429;
            AtomicInteger calls = new AtomicInteger();
            ChatModelClient client = new ChatModelClient.Https(endpoint -> { calls.incrementAndGet(); return connection; });
            ChatModelClient.Failure error = assertThrows(ChatModelClient.Failure.class,
                () -> client.analyze(ENDPOINT, "model", "synthetic", "RAG", null, gate()));
            assertEquals(scenario.equals("http") ? "HTTP_429" : scenario.equals("truncated") ? "OUTPUT_TRUNCATED" : "RESPONSE_JSON", error.code);
            assertEquals(1, calls.get()); assertTrue(connection.disconnected);
        }
    }
}
