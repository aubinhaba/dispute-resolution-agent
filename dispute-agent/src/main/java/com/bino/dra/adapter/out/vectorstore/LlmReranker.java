package com.bino.dra.adapter.out.vectorstore;

import com.bino.dra.adapter.out.support.Config;
import com.bino.dra.adapter.out.support.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LlmReranker implements DocumentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

    static final int SNIPPET_MAX_CHARS = 320;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final int topK;

    public LlmReranker(ChatClient chatClient, String systemPrompt, int topK) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.topK = Config.requireAtLeastOne(topK, "topK");
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> requestedOrder;
        try {
            RerankDraft draft = chatClient.prompt()
                    .system(systemPrompt)
                    .user(buildUserMessage(query, documents, topK))
                    .call()
                    .entity(RerankDraft.class);
            requestedOrder = draft == null ? List.of() : draft.orderedIds();
        } catch (RuntimeException e) {
            log.warn("LLM reranking unavailable, falling back to vector order", e);
            requestedOrder = List.of();
        }
        return reorder(documents, requestedOrder, topK);
    }

    static List<Document> reorder(List<Document> candidates, List<String> rankedIds, int topK) {
        Map<String, Document> byId = new LinkedHashMap<>();
        for (Document candidate : candidates) {
            byId.put(RuleChunks.chunkId(candidate), candidate);
        }

        Set<String> selected = new LinkedHashSet<>();
        if (rankedIds != null) {
            for (String id : rankedIds) {
                if (byId.containsKey(id) && selected.size() < topK) {
                    selected.add(id);
                }
            }
        }
        for (Document candidate : candidates) {
            if (selected.size() >= topK) {
                break;
            }
            selected.add(RuleChunks.chunkId(candidate));
        }

        List<Document> result = new ArrayList<>(selected.size());
        for (String id : selected) {
            result.add(byId.get(id));
        }
        return List.copyOf(result);
    }

    static String buildUserMessage(Query query, List<Document> candidates, int topK) {
        StringBuilder passages = new StringBuilder();
        for (Document candidate : candidates) {
            passages.append("\n- id: ").append(RuleChunks.chunkId(candidate)).append('\n')
                    .append("  text: ").append(snippet(candidate.getText())).append('\n');
        }

        return """
                # Dispute
                network: %s
                reason code: %s

                # Return at most %d identifiers

                # Candidate rule passages
                %s""".formatted(
                RuleQuery.networkOf(query), RuleQuery.reasonCodeOf(query), topK, passages);
    }

    private static String snippet(String text) {
        return Text.truncate(Text.flatten(text), SNIPPET_MAX_CHARS);
    }
}
