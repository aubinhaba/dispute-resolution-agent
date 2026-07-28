package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The RetrievalAugmentationAdvisor path the decision flow deliberately does NOT take (see ADR-0010)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RagAdvisorDemoIT {

    private static final Path MCP_SERVER_JAR =
            Path.of("../mcp-payment-server/target/mcp-payment-server-0.1.0-SNAPSHOT.jar");

    @Autowired
    private VectorStore ruleVectorStore;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @BeforeAll
    static void requireMcpServerJar() {
        assertThat(Files.exists(MCP_SERVER_JAR))
                .as("MCP server fat jar missing: run `mvn -DskipTests package` before the ITs")
                .isTrue();
    }

    @Test
    void the_advisor_performs_a_full_rag_in_one_declaration() {
        // Our blocks plug in unchanged: HeuristicReranker implements DocumentPostProcessor
        var advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(ruleVectorStore)
                        .topK(20)
                        .similarityThreshold(0.0)
                        .build())
                .documentPostProcessors(new HeuristicReranker(5))
                .build();

        ChatResponse response = chatClientBuilder.build().prompt()
                .advisors(advisor)
                .user("Under Visa reason code 10.4, what does a successful 3-D Secure "
                        + "authentication change for the merchant?")
                .call()
                .chatResponse();

        assertThat(response).isNotNull();

        // Recoverable documents make attestation possible, but nothing ties a sentence to one
        Object usedDocuments = response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        assertThat(usedDocuments).isInstanceOf(List.class);

        System.out.println("\n--- Answer written by the advisor (not used to decide) ---");
        System.out.println(response.getResult().getOutput().getText());
        System.out.println("\n--- Documents the advisor injected ---");
        ((List<?>) usedDocuments).forEach(d -> System.out.println("  " + ((Document) d).getId()));
    }
}
