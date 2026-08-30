package com.bino.dra.eval;

import com.bino.dra.domain.model.Decision;

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
