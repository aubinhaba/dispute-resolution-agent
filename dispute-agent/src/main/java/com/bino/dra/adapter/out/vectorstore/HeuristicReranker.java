package com.bino.dra.adapter.out.vectorstore;

import com.bino.dra.adapter.out.support.Config;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HeuristicReranker implements DocumentPostProcessor {

    static final double TIER_GOVERNS_DISPUTE = 200.0;
    static final double TIER_CROSS_CUTTING = 100.0;
    static final double TIER_DISTRACTOR = 0.0;

    static final double REDUNDANCY_PENALTY = 0.15;

    private final int topK;

    public HeuristicReranker(int topK) {
        this.topK = Config.requireAtLeastOne(topK, "topK");
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return greedySelection(documents, RuleQuery.reasonCodeOf(query), topK);
    }

    static List<Document> greedySelection(List<Document> candidates, String reasonCode, int topK) {
        List<Document> remaining = new ArrayList<>(candidates);
        List<Document> selected = new ArrayList<>();
        Map<String, Integer> pickedPerSheet = new HashMap<>();

        int toSelect = Math.min(topK, remaining.size());
        for (int i = 0; i < toSelect; i++) {
            Document best = remaining.stream()
                    .max(Comparator.comparingDouble(d -> score(d, reasonCode, pickedPerSheet)))
                    .orElseThrow();
            remaining.remove(best);
            selected.add(best);
            pickedPerSheet.merge(ruleIdOf(best), 1, Integer::sum);
        }
        return List.copyOf(selected);
    }

    static double score(Document document, String reasonCode, Map<String, Integer> pickedPerSheet) {
        String documentCode = RuleChunks.reasonCode(document);
        String appliesTo = RuleChunks.appliesTo(document);

        double tier;
        if ((!reasonCode.isEmpty() && reasonCode.equals(documentCode))
                || RuleCorpusLoader.applies(appliesTo, reasonCode)) {
            tier = TIER_GOVERNS_DISPUTE;
        } else if (RuleCorpusLoader.ANY.equals(documentCode)) {
            tier = TIER_CROSS_CUTTING;
        } else {
            tier = TIER_DISTRACTOR;
        }

        int alreadyPicked = pickedPerSheet.getOrDefault(ruleIdOf(document), 0);
        return tier + similarityOf(document) - alreadyPicked * REDUNDANCY_PENALTY;
    }

    private static double similarityOf(Document document) {
        Double score = document.getScore();
        return score == null ? 0.0 : score;
    }

    private static String ruleIdOf(Document document) {
        return RuleChunks.ruleId(document);
    }
}
