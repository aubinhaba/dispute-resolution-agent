package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DisputeDecision(
        String disputeId,
        Decision decision,
        // Self-reported by the model and poorly calibrated: audit it, never route on it alone
        double confidence,
        String rationale,
        String citedReasonCode,
        List<String> citedRulePassages,
        List<String> evidenceRefs,
        String agentVersion,
        Instant decidedAt
) {
    public DisputeDecision {
        Objects.requireNonNull(disputeId, "disputeId required");
        Objects.requireNonNull(decision, "decision required");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence out of [0,1]: " + confidence);
        }
        citedRulePassages = citedRulePassages == null ? List.of() : List.copyOf(citedRulePassages);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
