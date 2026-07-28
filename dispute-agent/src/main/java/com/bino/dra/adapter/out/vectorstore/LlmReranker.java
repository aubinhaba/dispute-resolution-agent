package com.bino.dra.adapter.out.vectorstore;

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

    // Context budget: full passages for every candidate would cost more than the decision
    static final int SNIPPET_MAX_CHARS = 320;

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final int topK;

    public LlmReranker(ChatClient chatClient, String systemPrompt, int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1, got: " + topK);
        }
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.topK = topK;
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
            // Degrade, never rethrow: reranking only improves an order it did not create
            log.warn("LLM reranking unavailable, falling back to vector order: {}", e.toString());
            requestedOrder = List.of();
        }
        return reorder(documents, requestedOrder, topK);
    }

    static List<Document> reorder(List<Document> candidates, List<String> rankedIds, int topK) {
        Map<String, Document> byId = new LinkedHashMap<>();
        for (Document candidate : candidates) {
            byId.put(candidate.getId(), candidate);
        }

        Set<String> selected = new LinkedHashSet<>();
        if (rankedIds != null) {
            for (String id : rankedIds) {
                // Candidate ids only: an invented citation would point at nothing (see ADR-0010)
                if (byId.containsKey(id) && selected.size() < topK) {
                    selected.add(id);
                }
            }
        }
        for (Document candidate : candidates) {
            if (selected.size() >= topK) {
                break;
            }
            selected.add(candidate.getId());
        }

        List<Document> result = new ArrayList<>(selected.size());
        for (String id : selected) {
            result.add(byId.get(id));
        }
        return List.copyOf(result);
    }

    static String buildUserMessage(Query query, List<Document> candidates, int topK) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dispute\n")
                .append("network: ").append(RuleQuery.networkOf(query)).append('\n')
                .append("reason code: ").append(RuleQuery.reasonCodeOf(query)).append('\n')
                .append("\n# Return at most ").append(topK).append(" identifiers\n")
                .append("\n# Candidate rule passages\n");

        for (Document candidate : candidates) {
            sb.append("\n- id: ").append(candidate.getId()).append('\n')
                    .append("  text: ").append(snippet(candidate.getText())).append('\n');
        }
        return sb.toString();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replaceAll(" +", " ").strip();
        return singleLine.length() <= SNIPPET_MAX_CHARS
                ? singleLine
                : singleLine.substring(0, SNIPPET_MAX_CHARS) + "...";
    }
}
