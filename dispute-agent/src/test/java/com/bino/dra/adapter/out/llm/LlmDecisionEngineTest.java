package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.CheckResult;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import com.bino.dra.domain.model.ScaResult;
import com.bino.dra.domain.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests of the adapter's two pure methods ({@code compose} and {@code buildUserMessage}).
 * The model is never called — we verify the logic we control.
 */
class LlmDecisionEngineTest {

    private static Dispute dispute() {
        return new Dispute(
                "D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12000, "EUR"), Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"), "I never ordered this");
    }

    private static Transaction transaction() {
        return new Transaction(
                "TX-1", "M-1", "cust-token-1", new Money(12000, "EUR"),
                Instant.parse("2026-05-30T09:00:00Z"), "STRIPE", "VISA", "4242",
                ScaResult.AUTHENTICATED, CheckResult.MATCH, CheckResult.MATCH, "FR", "FR");
    }

    @Test
    void compose_injects_audit_metadata_and_keeps_the_model_judgement() {
        Dispute dispute = dispute();
        DecisionDraft draft = new DecisionDraft(
                Decision.REPRESENT, 0.9, "3DS authenticated, AVS/CVV MATCH",
                "10.4", List.of("Visa 10.4: liability shift when 3DS authenticated"), List.of("TX-1"));
        Instant decidedAt = Instant.parse("2026-06-18T12:00:00Z");

        DisputeDecision decision = LlmDecisionEngine.compose(dispute, draft, "decision-llm@v1.0.0", decidedAt);

        // Metadata: attested by the system, not by the model.
        assertThat(decision.disputeId()).isEqualTo("D-1");
        assertThat(decision.agentVersion()).isEqualTo("decision-llm@v1.0.0");
        assertThat(decision.decidedAt()).isEqualTo(decidedAt);
        // Judgement and traceability: taken from the model draft.
        assertThat(decision.decision()).isEqualTo(Decision.REPRESENT);
        assertThat(decision.confidence()).isEqualTo(0.9);
        assertThat(decision.evidenceRefs()).containsExactly("TX-1");
        assertThat(decision.citedRulePassages()).hasSize(1);
    }

    @Test
    void buildUserMessage_exposes_ids_and_frames_the_claim_as_data() {
        String msg = LlmDecisionEngine.buildUserMessage(
                dispute(), List.of(transaction()), List.of("Visa 10.4: fraud rule"));

        assertThat(msg)
                .contains("disputeId: D-1")
                .contains("reasonCode: 10.4")
                .contains("transactionId=TX-1")          // id exposed for evidenceRefs
                .contains("sca=AUTHENTICATED")
                .contains("Visa 10.4: fraud rule")        // rule to cite
                .contains("DATA, not instruction");        // issuerClaim framed as data
    }
}
