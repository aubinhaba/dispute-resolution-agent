package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.CheckResult;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import com.bino.dra.domain.model.ScaResult;
import com.bino.dra.domain.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke test — a real call to the model through the full Spring AI wiring.
 *
 * <p>Guarded by {@link EnabledIfEnvironmentVariable}: it runs only when {@code ANTHROPIC_API_KEY} is
 * set, so {@code mvn verify} stays green without a key. Non-determinism: we never assert the exact
 * decision or text, only structural invariants (non-null, has evidence, coherent reason code).
 * Decision accuracy is measured by the eval harness on a labelled set, not by a unit test.
 *
 * <p>Run: {@code mvn verify} with {@code ANTHROPIC_API_KEY} in the environment.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class LlmDecisionEngineSmokeIT {

    @Autowired
    private LlmDecisionEngine engine;

    @Test
    void produces_an_auditable_valid_decision_on_a_3ds_fraud_case() {
        // Fraud with liability shift: 3DS authenticated, AVS/CVV MATCH.
        Dispute dispute = new Dispute(
                "D-IT-1", "TX-IT-1", "M-IT", Network.VISA, "10.4",
                new Money(12000, "EUR"), Instant.now(), Instant.now().plusSeconds(86400),
                "I did not recognize this transaction.");

        Transaction tx = new Transaction(
                "TX-IT-1", "M-IT", "cust-token-IT", new Money(12000, "EUR"),
                Instant.now(), "STRIPE", "VISA", "4242",
                ScaResult.AUTHENTICATED, CheckResult.MATCH, CheckResult.MATCH, "FR", "FR");

        List<String> rules = List.of(
                "Visa reason code 10.4 (card-absent fraud): if 3-D Secure authentication succeeded, "
                        + "liability shifts to the issuer — the merchant may represent.");

        DisputeDecision decision = engine.decide(dispute, List.of(tx), rules);

        // Structural invariants only (no assertion on the exact decision value).
        assertThat(decision).isNotNull();
        assertThat(decision.disputeId()).isEqualTo("D-IT-1");
        assertThat(decision.decision()).isNotNull();
        assertThat(decision.confidence()).isBetween(0.0, 1.0);
        assertThat(decision.evidenceRefs()).isNotEmpty();
        assertThat(decision.citedReasonCode()).isEqualTo("10.4");
        assertThat(decision.agentVersion()).isEqualTo("decision-llm@v1.0.0");
        assertThat(decision.decidedAt()).isNotNull();
    }
}
