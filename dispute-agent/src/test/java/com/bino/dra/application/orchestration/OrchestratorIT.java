package com.bino.dra.application.orchestration;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class OrchestratorIT {

    @Autowired
    private OrchestratorService orchestrator;

    private static Dispute dispute(String id, String txnId, String reasonCode, long minorUnits, String claim) {
        return new Dispute(
                id, txnId, "MERCH-ELEC-01", Network.VISA, reasonCode,
                new Money(minorUnits, "EUR"), Instant.now(), Instant.now().plusSeconds(86_400), claim);
    }

    @Test
    void produces_an_auditable_decision_backed_by_mcp_tools_and_rag() {
        DisputeDecision decision = orchestrator.resolve(
                dispute("EVAL-002", "TXN-EVAL-002", "10.4", 12_000L,
                        "I do not recognise this transaction on my statement."));

        // Structural invariants only: the model is free to vary, so its verdict is never asserted
        assertThat(decision.disputeId()).isEqualTo("EVAL-002");
        assertThat(decision.decision()).isNotNull();
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
        assertThat(decision.decidedAt()).isNotNull();
        assertThat(decision.evidenceRefs()).contains("TXN-EVAL-002");
        assertThat(decision.citedRulePassages()).isNotEmpty();
        assertThat(decision.citedRulePassages()).allSatisfy(p -> assertThat(p).isNotBlank());
        assertThat(decision.citedReasonCode()).isEqualTo("10.4");
        assertThat(decision.rationale()).isNotBlank();
    }

    // The only value assertion in this class, and it holds because this path bypasses the model
    @Test
    void escalates_above_the_threshold_on_a_strong_file_and_keeps_the_trail() {
        DisputeDecision decision = orchestrator.resolve(
                dispute("EVAL-004", "TXN-EVAL-004", "10.4", 150_000L,
                        "I am disputing this purchase, I did not make it."));

        assertThat(decision.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(decision.rationale()).startsWith("[AUTOMATIC ESCALATION");
        assertThat(decision.evidenceRefs()).contains("TXN-EVAL-004");
        assertThat(decision.citedRulePassages()).isNotEmpty();
        assertThat(decision.agentVersion()).isEqualTo("decision-llm@v1.0.0");
    }

    @Test
    void escalates_without_the_model_when_the_investigation_returns_no_evidence() {
        DisputeDecision decision = orchestrator.resolve(
                dispute("EVAL-UNKNOWN", "TXN-DOES-NOT-EXIST", "10.4", 5_000L, "Unknown transaction."));

        assertThat(decision.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(decision.rationale()).contains("no attested evidence");
        assertThat(decision.evidenceRefs()).isEmpty();
        assertThat(decision.agentVersion()).isEqualTo("orchestrator@v1.0.0");
        assertThat(decision.confidence()).isEqualTo(0.0);
        assertThat(decision.citedRulePassages()).isNotEmpty();
    }
}
