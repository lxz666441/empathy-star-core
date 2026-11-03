package example;

import com.lxz.empathystar.core.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Synthetic data and injected responses only. Never opens a model connection. */
public final class OfflineDemo {
    public static void main(String[] args) throws Exception {
        ConversationOrigin origin = new ConversationOrigin("demo-visible-frame", 1);
        Conversation frame = new Conversation(origin, Arrays.asList(
            new Conversation.Message(7, "我们用 RAG 给大模型补充外部知识。", Conversation.Side.INCOMING),
            new Conversation.Message(9, "我先整理检索资料，再一起确认。", Conversation.Side.OUTGOING)
        ), false);
        RequestGate gate = new RequestGate(origin, () -> origin, () -> true);
        ChatModelClient client = new OfflineClient();
        ChatAnalysis analysis = client.analyze("https://example.invalid/chat/completions", "offline-fixture", "",
            frame.messages.get(0).text, Collections.emptyList(), gate);
        System.out.println("OFFLINE DEMO: injected synthetic responses; no model request");
        System.out.println("Explanation: " + analysis.explanation);
        for (ChatAnalysis.Reply reply : analysis.replies) System.out.println(reply.label + ": " + reply.text);
        for (KnowledgeTerms.Term term : analysis.terms) System.out.println("Concept: " + term.text + " -> " + term.explanation);
        ConversationSelection selected = ConversationSelection.from(frame).select(new HashSet<>(Arrays.asList(7, 9)));
        ConversationSummary summary = client.summarize("https://example.invalid/chat/completions", "offline-fixture", "", selected, gate);
        for (ConversationSummary.Item item : summary.items)
            System.out.println(item.section.title + ": " + item.text + " " + item.sources);
    }

    private static final class OfflineClient implements ChatModelClient {
        @Override public ChatAnalysis analyze(String endpoint, String model, String key, String target,
                                             List<String> context, RequestGate gate) throws Exception {
            gate.check(null);
            return ChatAnalysis.parse("""
                {"explanation":"通过检索外部资料，为大模型回答提供参考。",
                 "insight":"对方在介绍技术方案，尚不足以判断情绪。",
                 "replies":[{"label":"自然回应","text":"明白，先把外部资料接入。"},
                            {"label":"进一步确认","text":"准备优先接入哪些资料？"},
                            {"label":"协作回应","text":"我可以先整理待检索的资料。"}],
                 "terms":[{"text":"RAG","explanation":"检索相关资料后辅助模型生成回答的方法。"},
                          {"text":"大模型","explanation":"这里指生成回答、需要补充外部资料的模型。"}]}
                """, target);
        }
        @Override public ConversationSummary summarize(String endpoint, String model, String key,
                                                        ConversationSelection selected, RequestGate gate) throws Exception {
            gate.check(null);
            if (!selected.isExplicitSelection() || !selected.belongsTo(gate.origin()))
                throw new IllegalArgumentException("INVALID_SELECTION");
            return ConversationSummary.parse("""
                {"topics":[{"text":"通过检索资料补充模型知识。","sources":[7,9]}],
                 "decisions":[],"open_questions":[],"actions":[],"for_me":[]}
                """, selected);
        }
    }
}
