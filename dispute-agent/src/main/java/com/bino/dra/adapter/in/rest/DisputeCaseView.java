package com.bino.dra.adapter.in.rest;

import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.DisputeCase;

import java.time.Instant;

public record DisputeCaseView(
        Provenanced<String> disputeId,
        CaseStatus status,
        DecisionView decision,
        String failureReason,
        Instant submittedAt,
        Instant completedAt) {
    public static DisputeCaseView from(DisputeCase disputeCase) {
        return new DisputeCaseView(
                Provenanced.untrusted(disputeCase.disputeId()),
                disputeCase.status(),
                disputeCase.decision() == null ? null : DecisionView.from(disputeCase.decision()),
                disputeCase.failureReason(),
                disputeCase.submittedAt(),
                disputeCase.completedAt());
    }
}
