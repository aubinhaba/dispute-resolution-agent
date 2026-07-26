package com.bino.dra.adapter.out.agent;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Asserts invariants of the investigation, never an exact model answer: the latter is flaky by nature
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class EvidenceAgentIT {

    @Autowired
    private LlmEvidenceAgent agent;

    @Test
    void investigates_a_fraud_dispute_starting_from_the_transaction() {
        Dispute dispute = new Dispute(
                "EVAL-002", "TXN-EVAL-002", "MERCH-ELEC-01", Network.VISA, "10.4",
                new Money(12000, "EUR"), Instant.now(), Instant.now().plusSeconds(86_400),
                "I did not recognize this transaction on my statement.");

        EvidenceBundle bundle = agent.gather(dispute);

        assertThat(bundle.toolsUsed()).isNotEmpty();
        assertThat(bundle.toolsUsed().getFirst()).isEqualTo("get_transaction");

        assertThat(bundle.evidenceRefs()).contains("TXN-EVAL-002");
        assertThat(bundle.isEmpty()).isFalse();

        assertThat(bundle.evidenceRefs()).contains("CUST-M4XA1");

        assertThat(bundle.budgetExhausted()).isFalse();
        assertThat(bundle.summary()).isNotBlank();
        assertThat(bundle.disputeId()).isEqualTo("EVAL-002");
        assertThat(bundle.agentVersion()).isEqualTo("evidence-llm@v1.0.0");
        assertThat(bundle.gatheredAt()).isNotNull();
    }

    @Test
    void routes_the_investigation_to_logistics_on_a_goods_not_received_reason() {
        Dispute dispute = new Dispute(
                "EVAL-003", "TXN-EVAL-003", "MERCH-FASHION-02", Network.VISA, "13.1",
                new Money(8000, "EUR"), Instant.now(), Instant.now().plusSeconds(86_400),
                "I never received the parcel for this order.");

        EvidenceBundle bundle = agent.gather(dispute);

        assertThat(bundle.toolsUsed()).contains("get_fulfillment_record");
        assertThat(bundle.evidenceRefs()).contains("TXN-EVAL-003");
        assertThat(bundle.budgetExhausted()).isFalse();
    }

    @Test
    void yields_an_empty_valid_bundle_when_the_transaction_does_not_exist() {
        Dispute dispute = new Dispute(
                "EVAL-UNKNOWN", "TXN-DOES-NOT-EXIST", "MERCH-ELEC-01", Network.VISA, "10.4",
                new Money(5000, "EUR"), Instant.now(), Instant.now().plusSeconds(86_400),
                "Unknown transaction.");

        EvidenceBundle bundle = agent.gather(dispute);

        assertThat(bundle.isEmpty()).isTrue();
        assertThat(bundle.toolsUsed()).isNotEmpty();
        assertThat(bundle.disputeId()).isEqualTo("EVAL-UNKNOWN");
    }
}
