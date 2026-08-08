package com.bino.dra.application.orchestration;

import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.EvidenceGatherer;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorServiceTest {

    private static final long THRESHOLD = 100_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T09:00:00Z"), ZoneOffset.UTC);

    private static final class StubGatherer implements EvidenceGatherer {
        private final EvidenceBundle bundle;
        private Dispute received;

        StubGatherer(EvidenceBundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public EvidenceBundle gather(Dispute dispute) {
            this.received = dispute;
            return bundle;
        }
    }

    private static final class StubRetriever implements RuleRetriever {
        private final List<String> passages;
        private String reasonCodeReceived;
        private Network networkReceived;

        StubRetriever(List<String> passages) {
            this.passages = passages;
        }

        @Override
        public List<String> retrieveRulePassages(String reasonCode, Network network) {
            this.reasonCodeReceived = reasonCode;
            this.networkReceived = network;
            return passages;
        }
    }

    private static final class StubEngine implements DecisionEngine {
        private final DisputeDecision response;
        private EvidenceBundle bundleReceived;
        private List<String> passagesReceived;
        private boolean called;

        StubEngine(DisputeDecision response) {
            this.response = response;
        }

        @Override
        public DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages) {
            this.called = true;
            this.bundleReceived = evidence;
            this.passagesReceived = rulePassages;
            return response;
        }
    }

    private static Dispute dispute(long minorUnits) {
        return new Dispute(
                "D-1", "TXN-EVAL-001", "M-1", Network.VISA, "10.4",
                new Money(minorUnits, "EUR"),
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z"),
                "I never authorised this transaction.");
    }

    private static EvidenceBundle attestedBundle() {
        return new EvidenceBundle(
                "D-1", "TXN-EVAL-001",
                "3DS authenticated transaction, AVS and CVV both matching.",
                List.of("SCA: AUTHENTICATED", "AVS: MATCH"),
                List.of("TXN-EVAL-001"),
                List.of("get_transaction"),
                false,
                "evidence-llm@v1.0.0",
                Instant.parse("2026-06-18T11:59:00Z"));
    }

    private static EvidenceBundle emptyBundle() {
        return new EvidenceBundle(
                "D-1", "TXN-EVAL-001",
                "Nothing unusual detected.",
                List.of(),
                List.of(),
                List.of(),
                false,
                "evidence-llm@v1.0.0",
                Instant.parse("2026-06-18T11:59:00Z"));
    }

    private static DisputeDecision modelResponse(Decision decision, double confidence, List<String> evidenceRefs) {
        return new DisputeDecision(
                "D-1", decision, confidence,
                "3DS authenticated, liability sits with the issuer.",
                "10.4",
                List.of("[visa-10.4#liability-shift] Where 3-D Secure authentication succeeded..."),
                evidenceRefs,
                "decision-llm@v1.0.0",
                Instant.parse("2026-06-18T12:00:00Z"));
    }

    private static OrchestratorService service(StubGatherer g, StubRetriever r, StubEngine e) {
        return new OrchestratorService(g, r, e, CLOCK, THRESHOLD, "orchestrator@v1.0.0");
    }

    @Test
    void dispatches_to_both_workers_with_the_right_keys() {
        StubGatherer gatherer = new StubGatherer(attestedBundle());
        StubRetriever retriever = new StubRetriever(List.of("rule"));
        Dispute dispute = dispute(12_000L);

        service(gatherer, retriever, new StubEngine(modelResponse(Decision.REPRESENT, 0.9, List.of("TXN-EVAL-001"))))
                .resolve(dispute);

        assertThat(gatherer.received).isSameAs(dispute);
        assertThat(retriever.reasonCodeReceived).isEqualTo("10.4");
        assertThat(retriever.networkReceived).isEqualTo(Network.VISA);
    }

    @Test
    void forwards_exactly_what_the_workers_produced_to_the_decision_engine() {
        EvidenceBundle bundle = attestedBundle();
        List<String> passages = List.of("[visa-10.4#liability-shift] ...");
        StubEngine engine = new StubEngine(modelResponse(Decision.REPRESENT, 0.9, List.of("TXN-EVAL-001")));

        service(new StubGatherer(bundle), new StubRetriever(passages), engine).resolve(dispute(12_000L));

        assertThat(engine.bundleReceived).isSameAs(bundle);
        assertThat(engine.passagesReceived).isEqualTo(passages);
    }

    @Test
    void keeps_the_model_decision_at_the_threshold() {
        DisputeDecision result = service(
                new StubGatherer(attestedBundle()),
                new StubRetriever(List.of("rule")),
                new StubEngine(modelResponse(Decision.REPRESENT, 0.9, List.of("TXN-EVAL-001"))))
                .resolve(dispute(THRESHOLD));

        assertThat(result.decision()).isEqualTo(Decision.REPRESENT);
        assertThat(result.rationale()).doesNotContain("AUTOMATIC ESCALATION");
    }

    @Test
    void escalates_above_the_threshold_even_when_the_model_says_represent() {
        DisputeDecision result = service(
                new StubGatherer(attestedBundle()),
                new StubRetriever(List.of("rule")),
                new StubEngine(modelResponse(Decision.REPRESENT, 0.95, List.of("TXN-EVAL-001"))))
                .resolve(dispute(THRESHOLD + 1));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.rationale())
                .startsWith("[AUTOMATIC ESCALATION")
                .contains("3DS authenticated");
        assertThat(result.citedRulePassages()).isNotEmpty();
        assertThat(result.evidenceRefs()).isNotEmpty();
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void escalates_an_empty_bundle_without_consulting_the_model() {
        StubEngine engine = new StubEngine(modelResponse(Decision.REPRESENT, 0.99, List.of("TXN-MADE-UP")));
        StubRetriever retriever = new StubRetriever(List.of("[visa-10.4#liability-shift] ..."));

        DisputeDecision result = service(new StubGatherer(emptyBundle()), retriever, engine)
                .resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.rationale()).contains("no attested evidence");
        assertThat(engine.called).isFalse();
        assertThat(result.citedRulePassages()).containsExactly("[visa-10.4#liability-shift] ...");
        assertThat(result.citedReasonCode()).isEqualTo("10.4");
        assertThat(result.agentVersion()).isEqualTo("orchestrator@v1.0.0");
        assertThat(result.decidedAt()).isEqualTo(Instant.parse("2026-08-07T09:00:00Z"));
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.evidenceRefs()).isEmpty();
    }

    @Test
    void evidence_refs_come_from_the_attested_bundle_not_from_the_model() {
        StubEngine engine = new StubEngine(
                modelResponse(Decision.REPRESENT, 0.9, List.of("TXN-NEVER-FETCHED")));

        DisputeDecision result = service(
                new StubGatherer(attestedBundle()),
                new StubRetriever(List.of("rule")),
                engine).resolve(dispute(12_000L));

        assertThat(result.evidenceRefs()).containsExactly("TXN-EVAL-001");
        assertThat(result.evidenceRefs()).doesNotContain("TXN-NEVER-FETCHED");
    }

    @Test
    void reproducibility_metadata_survives_composition() {
        DisputeDecision result = service(
                new StubGatherer(attestedBundle()),
                new StubRetriever(List.of("rule")),
                new StubEngine(modelResponse(Decision.REPRESENT, 0.9, List.of("TXN-EVAL-001"))))
                .resolve(dispute(12_000L));

        assertThat(result.disputeId()).isEqualTo("D-1");
        assertThat(result.agentVersion()).isEqualTo("decision-llm@v1.0.0");
        assertThat(result.decidedAt()).isEqualTo(Instant.parse("2026-06-18T12:00:00Z"));
        assertThat(result.citedReasonCode()).isEqualTo("10.4");
    }
}
