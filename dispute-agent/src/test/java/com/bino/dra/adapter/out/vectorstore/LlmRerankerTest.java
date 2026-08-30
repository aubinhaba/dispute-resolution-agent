package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRerankerTest {

    private static final int TOP_K = 3;

    private static final List<Document> CANDIDATES = List.of(
            chunk("visa-13.3#scope"),
            chunk("visa-10.4#liability-shift"),
            chunk("shared-3ds#principle"),
            chunk("visa-10.4#scope"),
            chunk("visa-12.6#scope"));

    @Test
    void the_order_proposed_by_the_model_is_applied() {
        List<Document> result = LlmReranker.reorder(CANDIDATES,
                List.of("shared-3ds#principle", "visa-10.4#liability-shift", "visa-10.4#scope"), TOP_K);

        assertThat(result).extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-10.4#liability-shift", "visa-10.4#scope");
    }

    @Test
    void an_invented_identifier_is_dropped_without_losing_the_others() {
        List<Document> result = LlmReranker.reorder(CANDIDATES,
                List.of("visa-10.4#liability", "shared-3ds#principle", "visa-10.4#scope"), TOP_K);

        assertThat(result).extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-10.4#scope", "visa-13.3#scope");
        assertThat(result).extracting(Document::getId).doesNotContain("visa-10.4#liability");
    }

    @Test
    void a_repeated_identifier_consumes_only_one_budget_slot() {
        List<Document> result = LlmReranker.reorder(CANDIDATES,
                List.of("shared-3ds#principle", "shared-3ds#principle", "visa-10.4#scope"), TOP_K);

        assertThat(result).extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-10.4#scope", "visa-13.3#scope");
    }

    @Test
    void a_short_list_is_completed_from_the_vector_order() {
        List<Document> result = LlmReranker.reorder(CANDIDATES, List.of("shared-3ds#principle"), TOP_K);

        assertThat(result).extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-13.3#scope", "visa-10.4#liability-shift");
    }

    @Test
    void an_empty_or_absent_response_falls_back_entirely_to_the_vector_order() {
        List<Document> onEmptyList = LlmReranker.reorder(CANDIDATES, List.of(), TOP_K);
        List<Document> onNull = LlmReranker.reorder(CANDIDATES, null, TOP_K);

        assertThat(onEmptyList).extracting(Document::getId)
                .containsExactly("visa-13.3#scope", "visa-10.4#liability-shift", "shared-3ds#principle");
        assertThat(onNull).extracting(Document::getId)
                .isEqualTo(onEmptyList.stream().map(Document::getId).toList());
    }

    @Test
    void the_model_cannot_exceed_the_passage_budget() {
        List<Document> result = LlmReranker.reorder(CANDIDATES,
                CANDIDATES.stream().map(Document::getId).toList(), TOP_K);

        assertThat(result).hasSize(TOP_K);
    }

    @Test
    void fewer_candidates_than_the_budget_does_not_throw() {
        List<Document> twoCandidates = List.of(chunk("visa-10.4#scope"), chunk("shared-3ds#principle"));

        assertThat(LlmReranker.reorder(twoCandidates, List.of("shared-3ds#principle"), TOP_K))
                .extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-10.4#scope");
    }

    @Test
    void the_user_message_carries_the_dispute_the_identifiers_and_truncated_snippets() {
        Query query = RuleQuery.of("10.4", "VISA");
        Document longChunk = Document.builder()
                .id("visa-10.4#scope")
                .text("x".repeat(LlmReranker.SNIPPET_MAX_CHARS + 200))
                .metadata(Map.of())
                .build();

        String message = LlmReranker.buildUserMessage(query, List.of(longChunk), TOP_K);

        assertThat(message).contains("network: VISA").contains("reason code: 10.4");
        assertThat(message).contains("id: visa-10.4#scope");
        assertThat(message).contains("...");
        assertThat(message.length()).isLessThan(LlmReranker.SNIPPET_MAX_CHARS + 300);
    }

    @Test
    void a_production_shaped_chunk_survives_the_fallback_to_the_vector_order() {
        List<Document> candidates = List.of(
                productionChunk("visa-10.4#scope"), productionChunk("shared-3ds#principle"));

        List<Document> result = LlmReranker.reorder(candidates, List.of(), TOP_K);

        assertThat(result).extracting(RuleChunks::chunkId)
                .containsExactly("visa-10.4#scope", "shared-3ds#principle");
    }

    @Test
    void the_identifier_shown_to_the_model_is_the_one_the_reordering_matches_on() {
        List<Document> candidates = List.of(
                productionChunk("visa-13.3#scope"), productionChunk("visa-10.4#scope"));

        String message = LlmReranker.buildUserMessage(RuleQuery.of("10.4", "VISA"), candidates, TOP_K);
        assertThat(message).contains("id: visa-10.4#scope");

        List<Document> reordered = LlmReranker.reorder(candidates, List.of("visa-10.4#scope"), TOP_K);
        assertThat(reordered).extracting(RuleChunks::chunkId)
                .startsWith("visa-10.4#scope");
    }

    private static Document chunk(String id) {
        return Document.builder().id(id).text("content of " + id).metadata(Map.of()).build();
    }

    private static Document productionChunk(String chunkId) {
        return Document.builder()
                .id(UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString())
                .text("content of " + chunkId)
                .metadata(Map.of(RuleCorpusLoader.META_CHUNK_ID, chunkId))
                .build();
    }
}
