package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EvidenceBundle(
        String disputeId,
        String transactionId,
        String summary,
        List<String> findings,
        List<String> evidenceRefs,
        List<String> toolsUsed,
        boolean budgetExhausted,
        String agentVersion,
        Instant gatheredAt
) {
    public EvidenceBundle {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(transactionId, "transactionId is required");
        findings = List.copyOf(findings);
        evidenceRefs = List.copyOf(evidenceRefs);
        toolsUsed = List.copyOf(toolsUsed);
        Objects.requireNonNull(agentVersion, "agentVersion is required (traceability)");
        Objects.requireNonNull(gatheredAt, "gatheredAt is required (traceability)");
    }

    public boolean isEmpty() {
        return evidenceRefs.isEmpty();
    }
}
