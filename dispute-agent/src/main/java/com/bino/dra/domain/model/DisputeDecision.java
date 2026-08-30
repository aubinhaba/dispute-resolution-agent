package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    public static final String ESCALATION_PREFIX = "[AUTOMATIC ESCALATION - ";

    private static final double NO_CONFIDENCE = 0.0;

    public DisputeDecision {
        Objects.requireNonNull(disputeId, "disputeId required");
        Objects.requireNonNull(decision, "decision required");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence out of [0,1]: " + confidence);
        }
        citedRulePassages = citedRulePassages == null ? List.of() : List.copyOf(citedRulePassages);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public static DisputeDecision escalation(String disputeId, String reason, String detail,
                                             String citedReasonCode, List<String> rulePassages,
                                             String agentVersion, Instant decidedAt) {
        return new DisputeDecision(disputeId, Decision.ESCALATE, NO_CONFIDENCE,
                ESCALATION_PREFIX + reason + "] " + detail,
                citedReasonCode, rulePassages, List.of(), agentVersion, decidedAt);
    }

    public DisputeDecision escalatedBecause(String reason) {
        return new DisputeDecision(disputeId, Decision.ESCALATE, confidence,
                ESCALATION_PREFIX + reason + "] " + rationale,
                citedReasonCode, citedRulePassages, evidenceRefs, agentVersion, decidedAt);
    }

    public DisputeDecision withEvidenceRefs(List<String> attestedRefs) {
        return new DisputeDecision(disputeId, decision, confidence, rationale, citedReasonCode,
                citedRulePassages, attestedRefs, agentVersion, decidedAt);
    }
}
