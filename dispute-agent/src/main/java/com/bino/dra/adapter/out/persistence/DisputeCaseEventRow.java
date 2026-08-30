package com.bino.dra.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("dispute_case_event")
record DisputeCaseEventRow(
        @Id Long seq,
        String disputeId,
        String status,
        String decision,
        Double confidence,
        String rationale,
        String citedReasonCode,
        String[] citedRulePassages,
        String[] evidenceRefs,
        String agentVersion,
        Instant decidedAt,
        String failureReason,
        Instant submittedAt,
        Instant completedAt,
        Instant occurredAt) {
    DisputeCaseEventRow {
        citedRulePassages = citedRulePassages == null ? null : citedRulePassages.clone();
        evidenceRefs = evidenceRefs == null ? null : evidenceRefs.clone();
    }

    @Override
    public String[] citedRulePassages() {
        return citedRulePassages == null ? null : citedRulePassages.clone();
    }

    @Override
    public String[] evidenceRefs() {
        return evidenceRefs == null ? null : evidenceRefs.clone();
    }
}
