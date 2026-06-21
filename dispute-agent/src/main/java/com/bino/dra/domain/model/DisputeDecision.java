package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Auditable decision produced by the orchestrator.
 *
 * <p>Traceability is mandatory: {@code citedRulePassages} (rule excerpts from RAG) and
 * {@code evidenceRefs} (transaction ids / signals) ground the decision. {@code confidence} is the
 * model's self-reported score — kept for audit, never used alone to route (it is poorly calibrated).
 */
public record DisputeDecision(
        String disputeId,
        Decision decision,
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
        // Defensive immutable copies: a stored audit trail must not be mutable after construction.
        citedRulePassages = citedRulePassages == null ? List.of() : List.copyOf(citedRulePassages);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
