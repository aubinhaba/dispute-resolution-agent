package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Third arm: the LLM reranker matches the free deterministic one here, one model call per dispute
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LlmRerankComparisonIT {

    private static final Path MCP_SERVER_JAR =
            Path.of("../mcp-payment-server/target/mcp-payment-server-0.1.0-SNAPSHOT.jar");

    @Autowired
    private VectorStore ruleVectorStore;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Value("classpath:prompts/compliance/rerank.v1.0.0.md")
    private Resource rerankPrompt;

    @BeforeAll
    static void requireMcpServerJar() {
        // Every @SpringBootTest here needs the fat jar: the MCP client is a mandatory bean
        assertThat(Files.exists(MCP_SERVER_JAR))
                .as("MCP server fat jar missing: run `mvn -DskipTests package` before the ITs")
                .isTrue();
    }

    @Test
    void llm_reranking_never_degrades_precision_below_no_reranking() throws IOException {
        int topK = RerankComparisonIT.TOP_K;

        double control = RerankComparisonIT.measure(ruleVectorStore,
                "no reranking (control)", RerankerConfig.noReranking(topK));
        double heuristic = RerankComparisonIT.measure(ruleVectorStore,
                "heuristic reranking", new HeuristicReranker(topK));
        double byLlm = RerankComparisonIT.measure(ruleVectorStore,
                "LLM reranking", new LlmReranker(
                        chatClientBuilder.build(),
                        rerankPrompt.getContentAsString(StandardCharsets.UTF_8),
                        topK));

        System.out.printf("%n>>> precision@5 - control %.2f | heuristic %.2f | LLM %.2f%n",
                control, heuristic, byLlm);

        // Asserting the LLM beats the heuristic would fail every other run
        assertThat(byLlm)
                .as("LLM reranking scores below no reranking at all: check the rerank prompt and "
                        + "the identifier filtering in LlmReranker")
                .isGreaterThanOrEqualTo(control);
    }
}
