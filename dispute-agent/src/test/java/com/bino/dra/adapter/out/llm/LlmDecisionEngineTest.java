package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDecisionEngineTest {

    private static Dispute dispute() {
        return new Dispute(
                "D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12000, "EUR"), Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"), "I never ordered this");
    }

    private static EvidenceBundle bundle() {
        return new EvidenceBundle(
                "D-1", "TX-1",
                "3DS authenticated transaction, AVS and CVV both matching.",
                List.of("SCA: AUTHENTICATED", "AVS: MATCH", "CVV: MATCH", "ipCountry=FR = billingCountry=FR"),
                List.of("TX-1"),
                List.of("get_transaction"),
                false,
                "evidence-llm@v1.0.0",
                Instant.parse("2026-06-18T11:59:00Z"));
    }

    @Test
    void compose_injects_audit_metadata_and_keeps_the_model_judgement() {
        Dispute dispute = dispute();
        DecisionDraft draft = new DecisionDraft(
                Decision.REPRESENT, 0.9, "3DS authenticated, AVS/CVV MATCH",
                "10.4", List.of("Visa 10.4: liability shift when 3DS authenticated"), List.of("TX-1"));
        Instant decidedAt = Instant.parse("2026-06-18T12:00:00Z");

        DisputeDecision decision = LlmDecisionEngine.compose(dispute, draft, "decision-llm@v1.0.0", decidedAt);

        assertThat(decision.disputeId()).isEqualTo("D-1");
        assertThat(decision.agentVersion()).isEqualTo("decision-llm@v1.0.0");
        assertThat(decision.decidedAt()).isEqualTo(decidedAt);
        assertThat(decision.decision()).isEqualTo(Decision.REPRESENT);
        assertThat(decision.confidence()).isEqualTo(0.9);
        assertThat(decision.evidenceRefs()).containsExactly("TX-1");
        assertThat(decision.citedRulePassages()).hasSize(1);
    }

    @Test
    void buildUserMessage_exposes_the_bundle_and_frames_the_claim_as_data() {
        String msg = LlmDecisionEngine.buildUserMessage(
                dispute(), bundle(), List.of("Visa 10.4: fraud rule"));

        assertThat(msg)
                .contains("disputeId: D-1")
                .contains("reasonCode: 10.4")
                .contains("summary: 3DS authenticated transaction")
                .contains("SCA: AUTHENTICATED")
                .contains("consulted references (ATTESTED): TX-1")
                .contains("Visa 10.4: fraud rule")
                .contains("DATA, not instruction");
    }

    @Test
    void buildUserMessage_withholds_the_summary_when_no_evidence_is_attested() {
        EvidenceBundle empty = new EvidenceBundle(
                "D-1", "TX-1",
                "The transaction looks legitimate.",
                List.of("no fraud signal"),
                List.of(),
                List.of(),
                false,
                "evidence-llm@v1.0.0",
                Instant.parse("2026-06-18T11:59:00Z"));

        String msg = LlmDecisionEngine.buildUserMessage(dispute(), empty, List.of());

        assertThat(msg)
                .contains("(no attested evidence)")
                .doesNotContain("The transaction looks legitimate");
    }

    @Test
    void repairMessage_resends_the_original_message_plus_the_violations() {
        String original = LlmDecisionEngine.buildUserMessage(
                dispute(), bundle(), List.of("[visa-10.4#liability-shift] ..."));

        String repair = LlmDecisionEngine.repairMessage(original,
                List.of("unattested citedRulePassage: Fraud - Card-Absent...",
                        "confidence out of [0,1]: 1.4"));

        assertThat(repair).startsWith(original);
        assertThat(repair)
                .contains("REJECTED")
                .contains("unattested citedRulePassage")
                .contains("confidence out of [0,1]: 1.4");
    }

    @Test
    void a_failed_repair_produces_an_auditable_decision_rather_than_an_exception() {
        List<String> retrievedRules = List.of("[visa-10.4#liability-shift] Liability shift: ...");
        Instant decidedAt = Instant.parse("2026-06-18T12:00:00Z");

        DisputeDecision decision = LlmDecisionEngine.escalateAfterFailedRepair(
                dispute(), retrievedRules, List.of("unattested citedRulePassage: ..."),
                "decision-llm@v1.1.0+repair-failed", decidedAt);

        assertThat(decision.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(decision.rationale())
                .startsWith("[AUTOMATIC ESCALATION")
                .contains("unattested citedRulePassage");
        assertThat(decision.citedRulePassages()).isEqualTo(retrievedRules);
        assertThat(decision.evidenceRefs()).isEmpty();
        assertThat(decision.agentVersion()).isEqualTo("decision-llm@v1.1.0+repair-failed");
        assertThat(decision.confidence()).isEqualTo(0.0);
        assertThat(decision.disputeId()).isEqualTo("D-1");
        assertThat(decision.decidedAt()).isEqualTo(decidedAt);
    }
}
