package com.bino.dra.eval;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.DisputeDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportReviewCountsTest {

    private static final List<DisputeDecision> DECISIONS = Stream.of(
                    "decision-llm@v1.2.0",
                    "decision-llm@v1.2.0+reviewed",
                    "decision-llm@v1.2.0+repaired+reviewed",
                    "decision-llm@v1.2.0+repaired+revised",
                    "decision-llm@v1.2.0+review-rejected",
                    "decision-llm@v1.2.0+review-inconclusive",
                    "decision-llm@v1.2.0+revision-failed",
                    "decision-llm@v1.2.0+repaired-xyz",
                    "decision-llm@v1.2.0+repair-failed")
            .map(EvalReportReviewCountsTest::decision)
            .toList();

    @Test
    void each_review_outcome_is_counted_once() {
        assertThat(EvalReport.ReviewCounts.of(DECISIONS))
                .isEqualTo(new EvalReport.ReviewCounts(2, 1, 1, 1, 1));
    }

    // With endsWith the first-pass rate would RISE when the critic is switched on, with no decision changed
    @Test
    void a_repair_followed_by_a_review_is_still_a_repair() {
        assertThat(EvalReport.count(DECISIONS, "+repaired")).isEqualTo(2);
        assertThat(EvalReport.count(DECISIONS, "+repair-failed")).isEqualTo(1);
    }

    private static DisputeDecision decision(String agentVersion) {
        return new DisputeDecision("D-1", Decision.ACCEPT, 0.5, "reason", "10.4",
                List.of("[visa-10.4#a] A passage."), List.of("TX-1"), agentVersion, Instant.EPOCH);
    }
}
