package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RerankComparisonIT {

    static final int CANDIDATES = 50;
    static final int TOP_K = 5;

    static final Map<String, Set<String>> QRELS = new LinkedHashMap<>(Map.of(
            "VISA|10.4", Set.of("visa-10.4", "shared-3ds", "visa-ce3.0", "shared-deadlines"),
            "VISA|13.1", Set.of("visa-13.1", "shared-deadlines", "shared-lifecycle"),
            "VISA|13.3", Set.of("visa-13.3", "shared-deadlines", "shared-lifecycle"),
            "VISA|12.6", Set.of("visa-12.6", "shared-deadlines", "shared-lifecycle"),
            "MASTERCARD|4837", Set.of("mc-4837", "shared-3ds", "shared-deadlines"),
            "MASTERCARD|4855", Set.of("mc-4855", "shared-deadlines", "shared-lifecycle"),
            "MASTERCARD|4853", Set.of("mc-4853", "shared-deadlines", "shared-lifecycle"),
            "MASTERCARD|4808", Set.of("mc-4808", "shared-deadlines")));

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
    void heuristic_reranking_beats_no_reranking() {
        double withoutReranking = measure(store, "no reranking (control)",
                RerankerConfig.noReranking(TOP_K));
        double heuristic = measure(store, "heuristic reranking", new HeuristicReranker(TOP_K));

        System.out.printf("%n>>> precision@5: control %.2f  ->  heuristic %.2f  (%+.0f %%)%n",
                withoutReranking, heuristic, (heuristic - withoutReranking) / withoutReranking * 100);

        assertThat(heuristic)
                .as("heuristic reranking no longer improves precision: check the corpus distractor "
                        + "sheets and the HeuristicReranker tiers")
                .isGreaterThan(withoutReranking);
    }

    @Test
    void on_a_fraud_dispute_the_liability_shift_reaches_the_context_window() {
        DocumentPostProcessor heuristic = new HeuristicReranker(TOP_K);
        DocumentPostProcessor control = RerankerConfig.noReranking(TOP_K);

        for (String fraudDispute : List.of("VISA|10.4", "MASTERCARD|4837")) {
            boolean with = bringsTheLiabilityShift(fraudDispute, heuristic);
            boolean without = bringsTheLiabilityShift(fraudDispute, control);
            System.out.printf("%n>>> %-16s liability shift in the top-5: control %s, heuristic %s%n",
                    fraudDispute, without ? "yes" : "no", with ? "yes" : "no");

            assertThat(with)
                    .as("no passage about authentication or the liability shift in the top-5 of %s",
                            fraudDispute)
                    .isTrue();
        }
    }

    private static boolean bringsTheLiabilityShift(String testCase, DocumentPostProcessor reranker) {
        return rerankFor(store, testCase, reranker).stream().anyMatch(document -> {
            Object section = document.getMetadata().get(RuleCorpusLoader.META_SECTION);
            String title = section instanceof String text ? text.toLowerCase(Locale.ROOT) : "";
            return title.contains("liability") || title.contains("authentic");
        });
    }

    static double measure(VectorStore vectorStore, String name, DocumentPostProcessor reranker) {
        StringBuilder report = new StringBuilder("\n--- %s ---\n".formatted(name));
        int relevant = 0;
        int total = 0;

        for (Map.Entry<String, Set<String>> testCase : QRELS.entrySet()) {
            List<Document> top = rerankFor(vectorStore, testCase.getKey(), reranker);
            long good = top.stream().filter(d -> testCase.getValue().contains(ruleIdOf(d))).count();
            relevant += (int) good;
            total += top.size();
            report.append("%-16s %d/%d  %s\n".formatted(
                    testCase.getKey(), good, top.size(), top.stream().map(Document::getId).toList()));
        }
        double precision = (double) relevant / total;
        report.append("precision@%d = %.2f\n".formatted(TOP_K, precision));
        System.out.println(report);
        return precision;
    }

    static List<Document> rerankFor(VectorStore vectorStore, String testCase,
                                    DocumentPostProcessor reranker) {
        String[] parts = testCase.split("\\|");
        Query query = RuleQuery.of(parts[1], parts[0]);

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.or(
                b.eq(RuleCorpusLoader.META_NETWORK, parts[0]),
                b.eq(RuleCorpusLoader.META_NETWORK, RuleCorpusLoader.ANY)).build();

        List<Document> candidates = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(CANDIDATES)
                .similarityThreshold(0.0)
                .filterExpression(filter)
                .build()
                .retrieve(query);

        return reranker.process(query, candidates);
    }

    private static String ruleIdOf(Document document) {
        Object value = document.getMetadata().get(RuleCorpusLoader.META_RULE_ID);
        return value instanceof String text ? text : "";
    }
}
