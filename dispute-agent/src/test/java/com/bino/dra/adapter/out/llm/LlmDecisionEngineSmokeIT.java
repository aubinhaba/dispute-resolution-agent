package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NoDatabase
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LlmDecisionEngineSmokeIT {

    @Autowired
    private LlmDecisionEngine engine;

    @Test
    void produces_an_auditable_valid_decision_on_a_3ds_fraud_case() {
        Dispute dispute = new Dispute(
                "D-IT-1", "TX-IT-1", "M-IT", Network.VISA, "10.4",
                new Money(12000, "EUR"), Instant.now(), Instant.now().plusSeconds(86400),
                "I did not recognize this transaction.");

        EvidenceBundle bundle = new EvidenceBundle(
                "D-IT-1", "TX-IT-1",
                "3-D Secure authenticated transaction, AVS and CVV both matching, "
                        + "IP country and billing country identical (FR).",
                List.of("SCA: AUTHENTICATED", "AVS: MATCH", "CVV: MATCH", "ipCountry=FR, billingCountry=FR"),
                List.of("TX-IT-1"),
                List.of("get_transaction"),
                false,
                "evidence-llm@v1.0.0",
                Instant.now());

        // Hand-written passages without the RuleRetriever prefix can never be attested (ADR-0014)
        List<String> rules = List.of(
                "[visa-10.4#liability-shift] Fraud - Card-Absent Environment - Liability shift: "
                        + "if 3-D Secure authentication succeeded, liability shifts to the issuer, "
                        + "so the merchant may represent.");

        DisputeDecision decision = engine.decide(dispute, bundle, rules);

        assertThat(decision).isNotNull();
        assertThat(decision.disputeId()).isEqualTo("D-IT-1");
        assertThat(decision.decision()).isNotNull();
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
        assertThat(decision.evidenceRefs()).isNotEmpty();
        assertThat(decision.citedReasonCode()).isEqualTo("10.4");
        assertThat(decision.agentVersion()).startsWith("decision-llm@v1.2.0");
        assertThat(decision.decidedAt()).isNotNull();
        assertThat(decision.citedRulePassages())
                .allSatisfy(passage -> assertThat(passage).matches("^\\[[^\\]]+].*"));
    }
}
