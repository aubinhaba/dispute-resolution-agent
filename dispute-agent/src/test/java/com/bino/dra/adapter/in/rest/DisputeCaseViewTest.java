package com.bino.dra.adapter.in.rest;

import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeCaseViewTest {

    private static final Instant WHEN = Instant.parse("2026-08-22T09:00:00Z");

    @Test
    void the_audit_trails_are_attested_even_on_a_model_decision() {
        DecisionView view = DecisionView.from(decision("decision-llm@v1.2.0"));

        assertThat(view.citedRulePassages().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.evidenceRefs().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.agentVersion().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.decidedAt().provenance()).isEqualTo(Provenance.ATTESTED);
    }

    @Test
    void a_model_judgement_is_labelled_MODEL() {
        DecisionView view = DecisionView.from(decision("decision-llm@v1.2.0"));

        assertThat(view.decision().provenance()).isEqualTo(Provenance.MODEL);
        assertThat(view.confidence().provenance()).isEqualTo(Provenance.MODEL);
        assertThat(view.rationale().provenance()).isEqualTo(Provenance.MODEL);
        assertThat(view.citedReasonCode().provenance()).isEqualTo(Provenance.MODEL);
    }

    @Test
    void a_decision_issued_by_the_orchestrator_alone_is_ATTESTED_throughout() {
        DecisionView view = DecisionView.from(decision("orchestrator@v1.0.0"));

        assertThat(view.decision().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.confidence().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.rationale().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.citedReasonCode().provenance()).isEqualTo(Provenance.ATTESTED);
    }

    @Test
    void a_repair_suffix_does_not_turn_model_output_into_a_guarantee() {
        assertThat(DecisionView.from(decision("decision-llm@v1.2.0+repaired")).decision().provenance())
                .isEqualTo(Provenance.MODEL);
        assertThat(DecisionView.from(decision("decision-llm@v1.2.0+repair-failed")).decision().provenance())
                .isEqualTo(Provenance.MODEL);
    }

    @Test
    void the_disputeId_returned_to_the_caller_is_declared_untrusted() {
        DisputeCaseView view = DisputeCaseView.from(DisputeCase.pending("D-1", WHEN));

        assertThat(view.disputeId().provenance()).isEqualTo(Provenance.UNTRUSTED);
        assertThat(view.disputeId().value()).isEqualTo("D-1");
    }

    @Test
    void a_case_still_PENDING_carries_no_decision_block() {
        DisputeCaseView view = DisputeCaseView.from(DisputeCase.pending("D-2", WHEN));

        assertThat(view.status()).isEqualTo(CaseStatus.PENDING);
        assertThat(view.decision()).isNull();
        assertThat(view.completedAt()).isNull();
    }

    private static DisputeDecision decision(String agentVersion) {
        return new DisputeDecision("D-1", Decision.REPRESENT, 0.82, "Evidence is consistent.", "13.1",
                List.of("[visa-13.1#proof] Proof of delivery"), List.of("TXN-EVAL-003"),
                agentVersion, WHEN);
    }
}
