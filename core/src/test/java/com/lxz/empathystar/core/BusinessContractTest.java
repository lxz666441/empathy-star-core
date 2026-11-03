package com.lxz.empathystar.core;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public final class BusinessContractTest {
    private final ConversationOrigin origin = new ConversationOrigin("synthetic-conversation", 1);
    private ConversationSelection frame(String... texts) {
        List<Conversation.Message> messages = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) messages.add(new Conversation.Message((i + 1) * 10,
            texts[i], i % 2 == 0 ? Conversation.Side.INCOMING : Conversation.Side.OUTGOING));
        return ConversationSelection.from(new Conversation(origin, messages, false));
    }
    private Set<Integer> ids(Integer... values) { return new HashSet<>(Arrays.asList(values)); }
    private ConversationSummary.Item topic(String text, Integer... refs) {
        return new ConversationSummary.Item(ConversationSummary.Section.TOPICS, text, Arrays.asList(refs));
    }
    static String analysisJson() {
        return "{\"explanation\":\"通过检索资料补充回答。\",\"insight\":\"对方在介绍技术方案。\","
            + "\"replies\":[{\"label\":\"自然回应\",\"text\":\"明白了。\"},{\"label\":\"澄清\",\"text\":\"有哪些资料？\"},"
            + "{\"label\":\"协作\",\"text\":\"我先整理资料。\"}],\"terms\":[{\"text\":\"RAG\",\"explanation\":\"检索资料辅助生成。\"},"
            + "{\"text\":\"PT\",\"explanation\":\"不应从GPT截取。\"},{\"text\":\"不存在的概念\",\"explanation\":\"应被过滤。\"}]}";
    }
    static String summaryJson(int source) {
        return "{\"topics\":[{\"text\":\"讨论技术方案\",\"sources\":[" + source
            + "]}],\"decisions\":[],\"open_questions\":[],\"actions\":[],\"for_me\":[]}";
    }

    @Test public void selectedIdsKeepOccurrenceIdentityAndSourceOrder() {
        ConversationSelection source = frame("same synthetic message", "middle", "same synthetic message");
        assertFalse(source.isExplicitSelection());
        Set<Integer> chosen = ids(30, 10);
        ConversationSelection selected = source.select(chosen); chosen.clear();
        assertTrue(selected.isExplicitSelection());
        assertEquals(10, selected.entries.get(0).id); assertEquals(30, selected.entries.get(1).id);
        assertNull(selected.find(20)); assertThrows(UnsupportedOperationException.class, () -> selected.entries.clear());
    }
    @Test public void sensitiveAndBlankMessagesDoNotRenumberRemainingIds() {
        ConversationSelection safe = frame("first", "sk-" + "x".repeat(40), " ", "last");
        assertEquals(2, safe.filteredCount); assertEquals(40, safe.entries.get(1).id);
        assertThrows(IllegalArgumentException.class, () -> safe.select(ids(20)));
    }
    @Test public void summaryRejectsUnknownAndEmptySelections() {
        ConversationSelection safe = frame("first");
        assertThrows(IllegalArgumentException.class, () -> safe.select(Collections.emptySet()));
        assertThrows(IllegalArgumentException.class, () -> safe.select(ids(10, 999)));
    }
    @Test public void selectedMessageAndCharacterLimitsRejectRatherThanTruncate() {
        String[] texts = new String[31]; Arrays.fill(texts, "synthetic");
        ConversationSelection safe = frame(texts); Set<Integer> chosen = new HashSet<>();
        for (int i = 1; i <= 31; i++) chosen.add(i * 10);
        assertThrows(IllegalArgumentException.class, () -> safe.select(chosen));
        chosen.remove(310); assertEquals(30, safe.select(chosen).entries.size());
        Arrays.fill(texts, "字".repeat(450));
        assertThrows(IllegalArgumentException.class, () -> frame(texts).select(chosen));
    }
    @Test public void conversationDefensivelyCopiesAndRejectsDuplicateIds() {
        Conversation.Message message = new Conversation.Message(3, "synthetic", Conversation.Side.UNKNOWN);
        List<Conversation.Message> input = new ArrayList<>(Collections.singletonList(message));
        Conversation conversation = new Conversation(origin, input, false); input.clear();
        assertEquals(1, conversation.messages.size());
        assertThrows(UnsupportedOperationException.class, () -> conversation.messages.clear());
        assertThrows(IllegalArgumentException.class, () -> new Conversation(origin, Arrays.asList(message, message), false));
    }
    @Test public void sourceAndRevisionAreIndependentFromAnyPlatform() {
        ConversationSelection safe = frame("synthetic");
        assertTrue(safe.belongsTo(new ConversationOrigin("synthetic-conversation", 1)));
        assertFalse(safe.belongsTo(new ConversationOrigin("synthetic-conversation", 2)));
        assertFalse(safe.belongsTo(new ConversationOrigin("another-conversation", 1)));
        assertFalse(safe.belongsTo(null));
    }
    @Test public void namesAreOptionalLabelsAndSensitiveNamesAreOmitted() {
        Conversation.Message first = new Conversation.Message(1, "synthetic", Conversation.Side.INCOMING, "test member");
        Conversation.Message second = new Conversation.Message(2, "synthetic", Conversation.Side.INCOMING, "sk-" + "x".repeat(40));
        ConversationSelection safe = ConversationSelection.from(new Conversation(origin, Arrays.asList(first, second), true));
        assertTrue(safe.group); assertTrue(safe.entries.get(0).speaker().contains("非唯一身份"));
        assertEquals("", safe.entries.get(1).speakerName);
    }
    @Test public void analysisPreservesThreeRepliesAndRejectsUngroundedConcepts() throws Exception {
        ChatAnalysis result = ChatAnalysis.parse(analysisJson(), "我们用 RAG 和 GPT。");
        assertEquals(3, result.replies.size()); assertEquals(1, result.terms.size());
        assertEquals("RAG", result.terms.get(0).text);
        KnowledgeTerms.Span span = result.terms.get(0).spans.get(0);
        assertEquals("RAG", "我们用 RAG 和 GPT。".substring(span.start, span.end));
    }
    @Test public void analysisRejectsMissingOrRepeatedReplies() throws Exception {
        JSONObject result = new JSONObject(analysisJson()); result.getJSONArray("replies").remove(2);
        assertThrows(Exception.class, () -> ChatAnalysis.parse(result.toString(), "RAG"));
        JSONObject repeated = new JSONObject(analysisJson());
        repeated.getJSONArray("replies").getJSONObject(1).put("text", "明白了。");
        assertThrows(Exception.class, () -> ChatAnalysis.parse(repeated.toString(), "RAG"));
    }
    @Test public void summaryRejectsReferenceToAnUnselectedMessage() throws Exception {
        ConversationSelection selected = frame("first", "second").select(ids(10));
        assertThrows(Exception.class, () -> ConversationSummary.parse(summaryJson(20), selected));
        assertEquals(10, (int) ConversationSummary.parse(summaryJson(10), selected).items.get(0).sources.get(0));
    }
    @Test public void summaryRejectsDuplicateEvidenceAndMissingTopics() {
        ConversationSelection selected = frame("synthetic").select(ids(10));
        assertThrows(IllegalArgumentException.class, () -> ConversationSummary.validate(Collections.singletonList(topic("topic", 10, 10)), selected));
        assertThrows(IllegalArgumentException.class, () -> ConversationSummary.validate(Collections.emptyList(), selected));
        assertThrows(IllegalArgumentException.class, () -> ConversationSummary.validate(Collections.singletonList(
            new ConversationSummary.Item(ConversationSummary.Section.ACTIONS, "hypothetical task", Collections.singletonList(10))), selected));
    }
    @Test public void summaryItemsAndReferencesAreDefensivelyCopied() {
        List<Integer> refs = new ArrayList<>(Collections.singletonList(10));
        ConversationSummary.Item item = new ConversationSummary.Item(ConversationSummary.Section.TOPICS, "topic", refs);
        ConversationSummary result = ConversationSummary.validate(Collections.singletonList(item), frame("synthetic").select(ids(10)));
        refs.clear(); assertEquals(1, result.items.get(0).sources.size());
        assertThrows(UnsupportedOperationException.class, () -> result.items.clear());
    }
    @Test public void contextOnlyIncludesPreviousEightOccurrences() {
        List<String> text = new ArrayList<>(); for (int i = 0; i < 12; i++) text.add("message" + i);
        List<String> context = BusinessPolicy.precedingContext(text, 10);
        assertEquals(8, context.size()); assertEquals("message2", context.get(0)); assertEquals("message9", context.get(7));
        assertFalse(context.contains("message10")); assertFalse(context.contains("message11"));
        assertTrue(BusinessPolicy.precedingContext(text, 0).isEmpty());
    }
    @Test public void consentRevocationSourceChangesAndCancellationRejectRequests() throws Exception {
        AtomicBoolean consent = new AtomicBoolean(false);
        AtomicReference<ConversationOrigin> current = new AtomicReference<>(origin);
        RequestGate gate = new RequestGate(origin, current::get, consent::get);
        assertEquals("UPLOAD_CONSENT_REQUIRED", assertThrows(ChatModelClient.Failure.class, () -> gate.check(null)).code);
        consent.set(true); gate.check(null);
        current.set(new ConversationOrigin("other", 1));
        assertEquals("STALE_SOURCE", assertThrows(ChatModelClient.Failure.class, () -> gate.check(null)).code);
        current.set(origin); gate.cancel();
        assertEquals("REQUEST_CANCELLED", assertThrows(ChatModelClient.Failure.class, () -> gate.check(null)).code);
    }
    @Test public void endpointsMustBeHttpsWithoutEmbeddedCredentialsOrFragments() {
        assertTrue(BusinessPolicy.isSecureModelEndpoint("https://example.invalid/chat/completions"));
        assertFalse(BusinessPolicy.isSecureModelEndpoint("http://example.invalid/"));
        assertFalse(BusinessPolicy.isSecureModelEndpoint("https://user:pass@example.invalid/"));
        assertFalse(BusinessPolicy.isSecureModelEndpoint("https://example.invalid/#fragment"));
    }
}
