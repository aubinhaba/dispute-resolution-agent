package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Baseline of vector search alone; suffixed IT because it is slow, not because it needs a service
class RuleRetrievalIT {

    private static final int CANDIDATES = 50;
    private static final int TOP_K = 5;
    private static final int EXPECTED_TOTAL_CHUNKS = 90;

    private static final Map<String, String> EXPECTED_BY_QUERY = Map.of(
            "VISA|10.4", "visa-10.4",
            "VISA|13.1", "visa-13.1",
            "VISA|13.3", "visa-13.3",
            "MASTERCARD|4837", "mc-4837",
            "MASTERCARD|4855", "mc-4855");

    private static VectorStore store;

    @BeforeAll
    static void indexCorpus() throws Exception {
        TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
        embeddingModel.afterPropertiesSet();

        Resource[] sheets = new PathMatchingResourcePatternResolver().getResources("classpath:rules/*.md");
        store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(RuleCorpusLoader.load(sheets));
    }

    @Test
    void the_whole_corpus_is_indexed() {
        List<Document> all = store.similaritySearch(SearchRequest.builder()
                .query("dispute")
                .topK(500)
                .similarityThreshold(0.0)
                .build());

        assertThat(all).hasSize(EXPECTED_TOTAL_CHUNKS);
    }

    @Test
    void the_network_filter_keeps_cross_cutting_sheets_and_excludes_the_other_network() {
        List<Document> candidates = retrieve("VISA", "10.4", CANDIDATES);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).extracting(d -> d.getMetadata().get(RuleCorpusLoader.META_NETWORK))
                .containsOnly("VISA", RuleCorpusLoader.ANY);
        assertThat(candidates).extracting(d -> d.getMetadata().get(RuleCorpusLoader.META_RULE_ID))
                .contains("shared-3ds");
    }

    @Test
    void recall_at_50_candidates_is_total_across_the_catalogue() {
        StringBuilder report = new StringBuilder("\nRecall of vector search alone\n");

        EXPECTED_BY_QUERY.forEach((query, expectedRuleId) -> {
            String[] parts = query.split("\\|");
            List<Document> candidates = retrieve(parts[0], parts[1], CANDIDATES);
            int rank = firstRankOf(candidates, expectedRuleId);
            report.append("%-16s %-16s %s\n".formatted(query, expectedRuleId,
                    rank < 0 ? "MISSING" : "#" + (rank + 1)));

            // Recall is unrecoverable downstream: no reranker surfaces a document never retrieved
            assertThat(rank)
                    .as("sheet %s missing from the %d candidates for %s", expectedRuleId, CANDIDATES, query)
                    .isGreaterThanOrEqualTo(0);
        });
        System.out.println(report);
    }

    @Test
    void cross_cutting_rules_are_retrieved_but_relegated_to_the_tail() {
        EXPECTED_BY_QUERY.keySet().forEach(query -> {
            String[] parts = query.split("\\|");
            int rank = firstRankOf(retrieve(parts[0], parts[1], CANDIDATES), "shared-3ds");

            assertThat(rank)
                    .as("3-D Secure missing from the candidates for %s", query)
                    .isGreaterThanOrEqualTo(0);
            // A reason code is an opaque token: no embedding model knows 10.4 means fraud
            assertThat(rank)
                    .as("3-D Secure already reaches the top-%d for %s: reranking would have "
                            + "nothing left to fix", TOP_K, query)
                    .isGreaterThanOrEqualTo(TOP_K);
        });
    }

    @Test
    void raw_precision_at_5_still_leaves_noise_to_remove() {
        int relevant = 0;
        int total = 0;
        StringBuilder report = new StringBuilder("\nBaseline precision@5 WITHOUT reranking\n");

        for (Map.Entry<String, String> testCase : EXPECTED_BY_QUERY.entrySet()) {
            String[] parts = testCase.getKey().split("\\|");
            List<Document> top = retrieve(parts[0], parts[1], TOP_K);

            long good = top.stream()
                    .filter(d -> testCase.getValue().equals(d.getMetadata().get(RuleCorpusLoader.META_RULE_ID)))
                    .count();
            relevant += (int) good;
            total += top.size();
            report.append("%-16s %d/%d relevant  %s\n".formatted(
                    testCase.getKey(), good, top.size(), top.stream().map(Document::getId).toList()));
        }

        double precision = (double) relevant / total;
        report.append("overall precision@5 = %.2f\n".formatted(precision));
        System.out.println(report);

        assertThat(precision)
                .as("vector search alone leaves no noise in the top-%d: the corpus lost its "
                        + "distractor sheets and RerankComparisonIT can no longer demonstrate anything", TOP_K)
                .isLessThan(1.0);
    }

    private static List<Document> retrieve(String network, String reasonCode, int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression networkFilter = b.or(
                b.eq(RuleCorpusLoader.META_NETWORK, network),
                b.eq(RuleCorpusLoader.META_NETWORK, RuleCorpusLoader.ANY)).build();

        return VectorStoreDocumentRetriever.builder()
                .vectorStore(store)
                .topK(topK)
                .similarityThreshold(0.0)
                .filterExpression(networkFilter)
                .build()
                .retrieve(RuleQuery.of(reasonCode, network));
    }

    private static int firstRankOf(List<Document> candidates, String ruleId) {
        for (int i = 0; i < candidates.size(); i++) {
            if (ruleId.equals(candidates.get(i).getMetadata().get(RuleCorpusLoader.META_RULE_ID))) {
                return i;
            }
        }
        return -1;
    }
}
