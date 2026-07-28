package com.bino.dra.adapter.out.agent;

import com.bino.dra.adapter.out.vectorstore.RuleCorpusLoader;
import com.bino.dra.adapter.out.vectorstore.RuleQuery;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.Network;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class LlmComplianceAgent implements RuleRetriever {

    private final VectorStore ruleVectorStore;
    private final DocumentPostProcessor reranker;
    private final int candidates;

    public LlmComplianceAgent(VectorStore ruleVectorStore,
                              DocumentPostProcessor ruleReranker,
                              @Value("${dra.rag.candidates}") int candidates) {
        this.ruleVectorStore = Objects.requireNonNull(ruleVectorStore, "ruleVectorStore required");
        this.reranker = Objects.requireNonNull(ruleReranker, "ruleReranker required");
        if (candidates < 1) {
            throw new IllegalArgumentException("dra.rag.candidates must be >= 1, got: " + candidates);
        }
        this.candidates = candidates;
    }

    @Override
    public List<String> retrieveRulePassages(String reasonCode, Network network) {
        // Only hard failure of this port: without a network the filter cannot be built
        Objects.requireNonNull(network, "network required to retrieve applicable rules");
        String code = reasonCode == null ? "" : reasonCode.strip();

        Query query = RuleQuery.of(code, network.name());

        List<Document> candidateChunks = VectorStoreDocumentRetriever.builder()
                .vectorStore(ruleVectorStore)
                .topK(candidates)
                // No threshold here: it would drop cross-cutting rules whose vectors sit far from
                // a reason-code query, and reranking is what sorts
                .similarityThreshold(0.0)
                .filterExpression(networkFilter(network))
                .build()
                .retrieve(query);

        return reranker.process(query, candidateChunks).stream()
                .map(LlmComplianceAgent::cite)
                .toList();
    }

    // "network OR ANY": dropping the ANY branch silently discards 3-D Secure, deadlines and lifecycle
    static Filter.Expression networkFilter(Network network) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.or(
                b.eq(RuleCorpusLoader.META_NETWORK, network.name()),
                b.eq(RuleCorpusLoader.META_NETWORK, RuleCorpusLoader.ANY)).build();
    }

    // The bracketed prefix is the audit trail: it points at a real, stable corpus position
    static String cite(Document chunk) {
        String title = metadata(chunk, RuleCorpusLoader.META_TITLE);
        String section = metadata(chunk, RuleCorpusLoader.META_SECTION);
        return "[%s] %s - %s: %s".formatted(chunk.getId(), title, section, bodyOf(chunk));
    }

    private static String bodyOf(Document chunk) {
        String text = chunk.getText() == null ? "" : chunk.getText();
        String[] parts = text.split("\n\n", 2);
        String body = parts.length == 2 ? parts[1] : text;
        // Flattened: LlmDecisionEngine renders passages as a bullet list
        return body.replace('\n', ' ').replaceAll(" +", " ").strip();
    }

    private static String metadata(Document chunk, String key) {
        Object value = chunk.getMetadata().get(key);
        return value instanceof String text ? text : "";
    }
}
