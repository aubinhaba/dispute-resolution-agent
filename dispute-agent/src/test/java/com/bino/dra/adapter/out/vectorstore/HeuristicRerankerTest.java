package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeuristicRerankerTest {

    private static final Query VISA_10_4_QUERY = RuleQuery.of("10.4", "VISA");

    @Test
    void a_document_on_the_right_reason_code_overtakes_a_better_vector_match() {
        Document distractor = chunk("visa-13.3#scope", "visa-13.3", "VISA", "13.3", 0.70);
        Document rightCode = chunk("visa-10.4#scope", "visa-10.4", "VISA", "10.4", 0.50);

        List<Document> reranked = new HeuristicReranker(2)
                .process(VISA_10_4_QUERY, List.of(distractor, rightCode));

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("visa-10.4#scope", "visa-13.3#scope");
    }

    @Test
    void a_strong_vector_match_never_displaces_a_rule_that_governs_the_dispute() {
        Document excellentDistractor = chunk("visa-13.3#scope", "visa-13.3", "VISA", "13.3", 0.95);
        Document mediocreGoverning = chunk("visa-10.4#scope", "visa-10.4", "VISA", "10.4", 0.20);

        List<Document> reranked = new HeuristicReranker(2)
                .process(VISA_10_4_QUERY, List.of(excellentDistractor, mediocreGoverning));

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("visa-10.4#scope", "visa-13.3#scope");
    }

    @Test
    void a_generic_cross_cutting_rule_outranks_a_distractor_but_not_a_governing_rule() {
        Document distractor = chunk("visa-13.3#scope", "visa-13.3", "VISA", "13.3", 0.95);
        Document crossCutting = chunk("shared-lifecycle#stages", "shared-lifecycle", "ANY", "ANY", 0.20);
        Document governing = chunk("visa-10.4#scope", "visa-10.4", "VISA", "10.4", 0.20);

        List<Document> reranked = new HeuristicReranker(3)
                .process(VISA_10_4_QUERY, List.of(distractor, crossCutting, governing));

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("visa-10.4#scope", "shared-lifecycle#stages", "visa-13.3#scope");
    }

    @Test
    void a_cross_cutting_sheet_declared_decisive_ranks_like_the_reason_code_sheet() {
        Document declared = applicableChunk("shared-3ds#principle", "shared-3ds", 0.20, ",10.4,4837,");
        Document distractor = chunk("visa-13.3#scope", "visa-13.3", "VISA", "13.3", 0.95);

        List<Document> reranked = new HeuristicReranker(2)
                .process(VISA_10_4_QUERY, List.of(distractor, declared));

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("shared-3ds#principle", "visa-13.3#scope");
    }

    @Test
    void the_redundancy_penalty_opens_the_top_without_evicting_the_dominant_sheet() {
        List<Document> candidates = List.of(
                chunk("visa-10.4#a", "visa-10.4", "VISA", "10.4", 0.62),
                chunk("visa-10.4#b", "visa-10.4", "VISA", "10.4", 0.61),
                chunk("visa-10.4#c", "visa-10.4", "VISA", "10.4", 0.60),
                chunk("visa-10.4#d", "visa-10.4", "VISA", "10.4", 0.59),
                applicableChunk("shared-3ds#principle", "shared-3ds", 0.28, ",10.4,4837,"));

        List<Document> reranked = new HeuristicReranker(5).process(VISA_10_4_QUERY, candidates);

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("visa-10.4#a", "visa-10.4#b", "visa-10.4#c",
                        "shared-3ds#principle", "visa-10.4#d");
    }

    @Test
    void truncation_returns_exactly_top_k_documents() {
        List<Document> candidates = List.of(
                chunk("visa-10.4#a", "visa-10.4", "VISA", "10.4", 0.60),
                chunk("visa-10.4#b", "visa-10.4", "VISA", "10.4", 0.59),
                chunk("visa-13.3#a", "visa-13.3", "VISA", "13.3", 0.58));

        assertThat(new HeuristicReranker(2).process(VISA_10_4_QUERY, candidates)).hasSize(2);
        assertThat(new HeuristicReranker(10).process(VISA_10_4_QUERY, candidates)).hasSize(3);
    }

    @Test
    void reranking_is_reproducible() {
        List<Document> candidates = List.of(
                chunk("visa-13.3#a", "visa-13.3", "VISA", "13.3", 0.70),
                chunk("visa-10.4#a", "visa-10.4", "VISA", "10.4", 0.50),
                chunk("shared-3ds#a", "shared-3ds", "ANY", "ANY", 0.60));

        HeuristicReranker reranker = new HeuristicReranker(3);
        List<Document> first = reranker.process(VISA_10_4_QUERY, candidates);
        List<Document> second = reranker.process(VISA_10_4_QUERY, candidates);

        assertThat(second).extracting(Document::getId)
                .isEqualTo(first.stream().map(Document::getId).toList());
    }

    @Test
    void without_query_context_the_reranker_falls_back_to_similarity_alone() {
        List<Document> candidates = List.of(
                chunk("visa-10.4#a", "visa-10.4", "VISA", "10.4", 0.40),
                chunk("visa-13.3#a", "visa-13.3", "VISA", "13.3", 0.90));

        List<Document> reranked = new HeuristicReranker(2)
                .process(new Query("free text"), candidates);

        assertThat(reranked).extracting(Document::getId)
                .containsExactly("visa-13.3#a", "visa-10.4#a");
    }

    @Test
    void an_empty_candidate_list_returns_an_empty_list() {
        assertThat(new HeuristicReranker(5).process(VISA_10_4_QUERY, List.of())).isEmpty();
        assertThat(new HeuristicReranker(5).process(VISA_10_4_QUERY, null)).isEmpty();
    }

    @Test
    void an_invalid_top_k_is_rejected_at_construction() {
        assertThatThrownBy(() -> new HeuristicReranker(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    private static Document chunk(String id, String ruleId, String network, String reasonCode,
                                  double similarity) {
        return build(id, ruleId, network, reasonCode, similarity, "");
    }

    private static Document applicableChunk(String id, String ruleId, double similarity,
                                            String appliesTo) {
        return build(id, ruleId, RuleCorpusLoader.ANY, RuleCorpusLoader.ANY, similarity, appliesTo);
    }

    private static Document build(String id, String ruleId, String network, String reasonCode,
                                  double similarity, String appliesTo) {
        return Document.builder()
                .id(id)
                .text("content of " + id)
                .score(similarity)
                .metadata(Map.of(
                        RuleCorpusLoader.META_RULE_ID, ruleId,
                        RuleCorpusLoader.META_NETWORK, network,
                        RuleCorpusLoader.META_REASON_CODE, reasonCode,
                        RuleCorpusLoader.META_APPLIES_TO, appliesTo,
                        RuleCorpusLoader.META_TITLE, "sheet " + ruleId,
                        RuleCorpusLoader.META_SECTION, "section"))
                .build();
    }
}
