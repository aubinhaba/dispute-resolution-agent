package com.bino.dra.adapter.out.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class RerankerConfig {

    private static final Logger log = LoggerFactory.getLogger(RerankerConfig.class);

    // Explicit switch, not @ConditionalOnProperty: the strategies must be comparable side by side
    @Bean
    public DocumentPostProcessor ruleReranker(
            ChatClient.Builder chatClientBuilder,
            @Value("${dra.rag.reranker}") String strategy,
            @Value("${dra.rag.top-k}") int topK,
            @Value("classpath:prompts/compliance/rerank.v1.0.0.md") Resource rerankPrompt) {

        log.info("Reranking strategy: {} (top-k = {})", strategy, topK);

        return switch (strategy) {
            case "heuristic" -> new HeuristicReranker(topK);
            case "llm" -> new LlmReranker(chatClientBuilder.build(), read(rerankPrompt), topK);
            case "none" -> noReranking(topK);
            default -> throw new IllegalStateException(
                    "Unknown dra.rag.reranker: '" + strategy + "' (expected heuristic | llm | none)");
        };
    }

    // Control arm, not a production strategy
    static DocumentPostProcessor noReranking(int topK) {
        return (query, documents) -> documents == null
                ? List.<Document>of()
                : documents.stream().limit(topK).toList();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unreadable reranking prompt", e);
        }
    }
}
