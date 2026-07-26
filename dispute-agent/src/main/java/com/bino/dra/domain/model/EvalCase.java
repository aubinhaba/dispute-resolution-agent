package com.bino.dra.domain.model;

import java.util.Objects;

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
