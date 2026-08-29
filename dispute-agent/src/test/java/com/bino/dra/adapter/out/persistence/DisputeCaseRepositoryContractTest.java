package com.bino.dra.adapter.out.persistence;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class DisputeCaseRepositoryContractTest {

    protected static final Instant SUBMITTED_AT = Instant.parse("2026-08-22T09:00:00Z");
    protected static final Instant COMPLETED_AT = Instant.parse("2026-08-22T09:00:20Z");

    protected abstract DisputeCaseRepository repository();

    @Test
    void a_first_claim_is_granted_and_readable_back() {
        DisputeCase claimed = repository().claim(DisputeCase.pending("D-1", SUBMITTED_AT)).orElseThrow();

        assertThat(claimed.status()).isEqualTo(CaseStatus.PENDING);
        assertThat(repository().findById("D-1")).get()
                .extracting(DisputeCase::status, DisputeCase::submittedAt, DisputeCase::completedAt)
                .containsExactly(CaseStatus.PENDING, SUBMITTED_AT, null);
    }

    @Test
    void a_second_claim_on_the_same_id_is_refused_without_overwriting_anything() {
        repository().claim(DisputeCase.pending("D-2", SUBMITTED_AT));

        assertThat(repository().claim(DisputeCase.pending("D-2", COMPLETED_AT))).isEmpty();
        assertThat(repository().findById("D-2")).get()
                .extracting(DisputeCase::submittedAt).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void the_transition_to_DONE_keeps_both_audit_trails() {
        DisputeCase pending = repository().claim(DisputeCase.pending("D-3", SUBMITTED_AT)).orElseThrow();

        repository().save(pending.done(decision("D-3"), COMPLETED_AT));

        assertThat(repository().findById("D-3")).get().satisfies(read -> {
            assertThat(read.status()).isEqualTo(CaseStatus.DONE);
            assertThat(read.completedAt()).isEqualTo(COMPLETED_AT);
            assertThat(read.decision().decision()).isEqualTo(Decision.REPRESENT);
            assertThat(read.decision().confidence()).isEqualTo(0.82);
            assertThat(read.decision().citedReasonCode()).isEqualTo("13.1");
            assertThat(read.decision().agentVersion()).isEqualTo("decision-llm@v1.2.0");
            assertThat(read.decision().decidedAt()).isEqualTo(COMPLETED_AT);
            assertThat(read.decision().citedRulePassages())
                    .containsExactly("[visa-13.1#proof] Proof of delivery", "[visa-13.1#limits] Time limits");
            assertThat(read.decision().evidenceRefs()).containsExactly("TXN-EVAL-003", "get_transaction");
        });
    }

    @Test
    void a_failure_carries_its_reason_and_no_decision() {
        DisputeCase pending = repository().claim(DisputeCase.pending("D-4", SUBMITTED_AT)).orElseThrow();

        repository().save(pending.failed("BadRequestException", COMPLETED_AT));

        assertThat(repository().findById("D-4")).get()
                .extracting(DisputeCase::status, DisputeCase::failureReason, DisputeCase::decision)
                .containsExactly(CaseStatus.FAILED, "BadRequestException", null);
    }

    @Test
    void a_decision_without_citations_survives_storage_without_becoming_null() {
        DisputeCase pending = repository().claim(DisputeCase.pending("D-5", SUBMITTED_AT)).orElseThrow();
        DisputeDecision escalation = new DisputeDecision("D-5", Decision.ESCALATE, 0.0,
                "[AUTOMATIC ESCALATION] deadline", null, List.of(), List.of(),
                "orchestrator@v1.0.0", COMPLETED_AT);

        repository().save(pending.done(escalation, COMPLETED_AT));

        assertThat(repository().findById("D-5")).get().satisfies(read -> {
            assertThat(read.decision().citedRulePassages()).isEmpty();
            assertThat(read.decision().evidenceRefs()).isEmpty();
            assertThat(read.decision().citedReasonCode()).isNull();
        });
    }

    @Test
    void an_id_that_was_never_claimed_is_not_found() {
        assertThat(repository().findById("never-claimed")).isEmpty();
    }

    protected static DisputeDecision decision(String disputeId) {
        return new DisputeDecision(disputeId, Decision.REPRESENT, 0.82,
                "Delivery evidence is consistent.", "13.1",
                List.of("[visa-13.1#proof] Proof of delivery", "[visa-13.1#limits] Time limits"),
                List.of("TXN-EVAL-003", "get_transaction"),
                "decision-llm@v1.2.0", COMPLETED_AT);
    }
}
