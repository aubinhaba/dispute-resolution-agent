package com.bino.dra.adapter.out.agent;

import com.bino.dra.adapter.out.support.Config;
import com.bino.dra.adapter.out.vectorstore.RuleChunks;
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
        this.candidates = Config.requireAtLeastOne(candidates, "dra.rag.candidates");
    }

    @Override
    public List<String> retrieveRulePassages(String reasonCode, Network network) {
        Objects.requireNonNull(network, "network required to retrieve applicable rules");
        String code = reasonCode == null ? "" : reasonCode.strip();

        Query query = RuleQuery.of(code, network.name());

        List<Document> candidateChunks = VectorStoreDocumentRetriever.builder()
                .vectorStore(ruleVectorStore)
                .topK(candidates)
                .similarityThreshold(0.0)
                .filterExpression(networkFilter(network))
                .build()
                .retrieve(query);

        return reranker.process(query, candidateChunks).stream()
                .map(LlmComplianceAgent::cite)
                .toList();
    }

    static Filter.Expression networkFilter(Network network) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.or(
                b.eq(RuleCorpusLoader.META_NETWORK, network.name()),
                b.eq(RuleCorpusLoader.META_NETWORK, RuleCorpusLoader.ANY)).build();
    }

    static String cite(Document chunk) {
        return "[%s] %s - %s: %s".formatted(RuleChunks.chunkId(chunk), RuleChunks.title(chunk),
                RuleChunks.section(chunk), RuleChunks.body(chunk));
    }
}
