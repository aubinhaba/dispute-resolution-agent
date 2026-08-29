package com.bino.dra.application.orchestration;

import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.application.port.out.EvidenceGatherer;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The DONE/FAILED boundary: misplaced, it presents an escalation as a system outage
class DisputeJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void a_produced_decision_yields_a_DONE_case_carrying_it() {
        DisputeCaseRepository repository = new CaseRepositoryDouble();
        DisputeJobService service = new DisputeJobService(
                orchestrator(decision("D-1", Decision.REPRESENT)), repository, CLOCK);

        service.run(dispute("D-1"));

        assertThat(repository.findById("D-1")).get()
                .extracting(DisputeCase::status, c -> c.decision().decision())
                .containsExactly(CaseStatus.DONE, Decision.REPRESENT);
    }

    @Test
    void an_ESCALATE_is_a_processing_success_and_not_a_failure() {
        // Protects the total contract: a dispute yields a decision even when declining (ADR-0014)
        DisputeCaseRepository repository = new CaseRepositoryDouble();
        DisputeJobService service = new DisputeJobService(
                orchestrator(decision("D-2", Decision.ESCALATE)), repository, CLOCK);

        service.run(dispute("D-2"));

        assertThat(repository.findById("D-2")).get()
                .extracting(DisputeCase::status, DisputeCase::failureReason)
                .containsExactly(CaseStatus.DONE, null);
    }

    @Test
    void an_infrastructure_failure_yields_a_FAILED_case_and_does_not_propagate() {
        DisputeCaseRepository repository = new CaseRepositoryDouble();
        OrchestratorService failing = failingOrchestrator();
        DisputeJobService service = new DisputeJobService(failing, repository, CLOCK);

        service.run(dispute("D-3"));

        assertThat(repository.findById("D-3")).get()
                .extracting(DisputeCase::status, DisputeCase::completedAt)
                .containsExactly(CaseStatus.FAILED, NOW);
    }

    @Test
    void the_failure_reason_never_echoes_the_dispute_content() {
        // An echoed exception message would replay in the trail what was just refused
        DisputeCaseRepository repository = new CaseRepositoryDouble();
        DisputeJobService service = new DisputeJobService(failingOrchestrator(), repository, CLOCK);

        service.run(dispute("D-4"));

        assertThat(repository.findById("D-4")).get()
                .extracting(DisputeCase::failureReason)
                .isEqualTo("IllegalStateException");
    }

    private static OrchestratorService orchestrator(DisputeDecision returned) {
        return build((dispute, bundle, passages) -> returned);
    }

    private static OrchestratorService failingOrchestrator() {
        return build((dispute, bundle, passages) -> {
            throw new IllegalStateException("model unreachable for TXN-D-4");
        });
    }

    // A real orchestrator: testing a boundary against an imitation validates the imitation
    private static OrchestratorService build(DecisionEngine engine) {
        EvidenceGatherer evidence = dispute -> new EvidenceBundle(
                dispute.disputeId(), dispute.transactionId(), "summary", List.of("one finding"),
                List.of("TXN-1"), List.of("get_transaction"), false, "evidence-stub@v1", NOW);
        RuleRetriever rules = (reasonCode, network) -> List.of("[visa-10.4#a] A rule passage.");
        return new OrchestratorService(evidence, rules, engine, new PromptSafetyGuard(),
                CLOCK, 100_000L, 3, "orchestrator@v1.0.0");
    }

    private static DisputeDecision decision(String id, Decision verdict) {
        return new DisputeDecision(id, verdict, 0.8, "rationale", "10.4",
                List.of("[visa-10.4#a] A rule passage."), List.of("TXN-1"),
                "decision-stub@v1", NOW);
    }

    private static Dispute dispute(String id) {
        return new Dispute(id, "TXN-" + id, "MERCH-1", Network.VISA, "10.4",
                new Money(4_500L, "EUR"), NOW,
                NOW.plus(Duration.ofDays(30)), "Transaction not recognised.");
    }
}
