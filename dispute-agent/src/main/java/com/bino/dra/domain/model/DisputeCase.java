package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.Objects;

// Every transition returns a new instance: an audit trail that can be rewritten is not one
public record DisputeCase(
        String disputeId,
        CaseStatus status,
        DisputeDecision decision,
        String failureReason,
        Instant submittedAt,
        Instant completedAt
) {

    public DisputeCase {
        Objects.requireNonNull(disputeId, "disputeId required");
        Objects.requireNonNull(status, "status required");
        Objects.requireNonNull(submittedAt, "submittedAt required");
    }

    public static DisputeCase pending(String disputeId, Instant submittedAt) {
        return new DisputeCase(disputeId, CaseStatus.PENDING, null, null, submittedAt, null);
    }

    public DisputeCase done(DisputeDecision decision, Instant completedAt) {
        Objects.requireNonNull(decision, "decision required for a DONE case");
        return new DisputeCase(disputeId, CaseStatus.DONE, decision, null, submittedAt, completedAt);
    }

    // reason is a technical cause, never an echoed input field: that string survives in the trail
    public DisputeCase failed(String reason, Instant completedAt) {
        return new DisputeCase(disputeId, CaseStatus.FAILED, null, reason, submittedAt, completedAt);
    }
}
