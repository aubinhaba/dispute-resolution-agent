package com.bino.dra.domain.model;

import java.util.Objects;

/**
 * One labelled evaluation case — ground truth for the eval harness. Maps a known dispute to its
 * expected decision and reason code (scored by exact-match).
 */
public record EvalCase(
        String disputeId,
        Decision expectedDecision,
        String expectedReasonCode
) {
    public EvalCase {
        Objects.requireNonNull(disputeId, "disputeId required");
        Objects.requireNonNull(expectedDecision, "expectedDecision required");
        Objects.requireNonNull(expectedReasonCode, "expectedReasonCode required");
    }
}
