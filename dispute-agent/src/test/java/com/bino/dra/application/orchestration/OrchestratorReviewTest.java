package com.bino.dra.application.orchestration;

import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.DecisionReviewer;
import com.bino.dra.application.port.out.DependencyUnavailableException;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import com.bino.dra.domain.model.ReviewVerdict;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorReviewTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:00:00Z");
    private static final long THRESHOLD = 100_000L;
    private static final String OBJECTION = "the rationale relies on a successful 3DS, the finding says FAILED";

    @Test
    void reviewer_disabled_the_critic_is_never_consulted() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Coherent());

        DisputeDecision result = service(new StubEngine(), critic, false).resolve(dispute(12_000L));

        assertThat(critic.reviewed).isEmpty();
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest");
    }

    @Test
    void a_coherent_verdict_leaves_the_decision_intact_and_the_version_says_so() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Coherent());
        StubEngine engine = new StubEngine();

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.REPRESENT);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+reviewed");
        assertThat(critic.reviewed).hasSize(1);
        assertThat(engine.objectionsReceived).isNull();
    }

    @Test
    void an_incoherent_verdict_makes_the_generator_revise_and_the_revision_is_reviewed() {
        StubReviewer critic = new StubReviewer(
                new ReviewVerdict.Incoherent(List.of(OBJECTION)), new ReviewVerdict.Coherent());
        StubEngine engine = new StubEngine();

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(engine.objectionsReceived).containsExactly(OBJECTION);
        assertThat(engine.revisions).isEqualTo(1);
        assertThat(result.decision()).isEqualTo(Decision.ACCEPT);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+revised");
        assertThat(critic.reviewed).extracting(DisputeDecision::decision)
                .containsExactly(Decision.REPRESENT, Decision.ACCEPT);
    }

    @Test
    void a_persistent_disagreement_escalates_keeping_the_analysis_and_the_objections() {
        StubReviewer critic = new StubReviewer(
                new ReviewVerdict.Incoherent(List.of(OBJECTION)),
                new ReviewVerdict.Incoherent(List.of("the cited rule does not cover this reason code")));
        StubEngine engine = new StubEngine();

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+review-rejected");
        assertThat(result.rationale())
                .startsWith("[AUTOMATIC ESCALATION - persistent reviewer disagreement")
                .contains("the cited rule does not cover this reason code")
                .contains("revised analysis");
        assertThat(critic.reviewed).hasSize(2);
        assertThat(engine.revisions).isEqualTo(1);
        assertThat(result.evidenceRefs()).containsExactly("TXN-1");
    }

    @Test
    void an_unusable_review_fails_CLOSED_without_asking_for_a_revision() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Inconclusive("unparsable answer"));
        StubEngine engine = new StubEngine();

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+review-inconclusive");
        assertThat(result.rationale()).contains("unparsable answer");
        assertThat(engine.objectionsReceived).isNull();
    }

    @Test
    void a_critic_outage_becomes_a_motivated_escalation_never_an_exception() {
        DecisionReviewer down = (dispute, evidence, passages, proposed) -> {
            throw new DependencyUnavailableException("anthropic", "circuit breaker open", null);
        };

        DisputeDecision result = service(new StubEngine(), down, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).endsWith("+review-inconclusive");
        assertThat(result.rationale()).contains("anthropic").contains("circuit breaker open");
    }

    @Test
    void a_model_ESCALATE_is_not_reviewed_a_human_will_read_it_anyway() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Coherent());
        StubEngine engine = new StubEngine(Decision.ESCALATE, Decision.ACCEPT);

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(critic.reviewed).isEmpty();
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest");
    }

    @Test
    void a_deterministic_escalation_skips_the_review() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Coherent());

        DisputeDecision result = service(new StubEngine(), critic, true).resolve(dispute(THRESHOLD + 1));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(critic.reviewed).isEmpty();
    }

    @Test
    void a_revision_that_gives_up_is_not_reviewed() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Incoherent(List.of(OBJECTION)));
        StubEngine engine = new StubEngine(Decision.REPRESENT, Decision.ESCALATE);

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+revision-failed");
        assertThat(critic.reviewed).hasSize(1);
    }

    @Test
    void an_outage_during_the_revision_is_not_blamed_on_the_review() {
        StubReviewer critic = new StubReviewer(new ReviewVerdict.Incoherent(List.of(OBJECTION)));
        DecisionEngine engine = new StubEngine() {
            @Override
            public DisputeDecision revise(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                                          DisputeDecision previous, List<String> objections) {
                throw new DependencyUnavailableException("anthropic", "unreachable", null);
            }
        };

        DisputeDecision result = service(engine, critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+revision-failed");
        assertThat(result.rationale()).contains("revision interrupted").contains("initial analysis");
    }

    @Test
    void an_unusable_second_review_fails_CLOSED_too() {
        StubReviewer critic = new StubReviewer(
                new ReviewVerdict.Incoherent(List.of(OBJECTION)),
                new ReviewVerdict.Inconclusive("unparsable answer"));

        DisputeDecision result = service(new StubEngine(), critic, true).resolve(dispute(12_000L));

        assertThat(result.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(result.agentVersion()).isEqualTo("decision-llm@vTest+review-inconclusive");
    }

    private static class StubEngine implements DecisionEngine {
        private final Decision first;
        private final Decision revised;
        private List<String> objectionsReceived;
        private int revisions;

        StubEngine() {
            this(Decision.REPRESENT, Decision.ACCEPT);
        }

        StubEngine(Decision first, Decision revised) {
            this.first = first;
            this.revised = revised;
        }

        @Override
        public DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages) {
            return decision(first, "initial analysis");
        }

        @Override
        public DisputeDecision revise(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                                      DisputeDecision previous, List<String> objections) {
            this.objectionsReceived = objections;
            this.revisions++;
            return decision(revised, "revised analysis");
        }
    }

    private static final class StubReviewer implements DecisionReviewer {
        private final Deque<ReviewVerdict> verdicts;
        private final List<DisputeDecision> reviewed = new ArrayList<>();

        StubReviewer(ReviewVerdict... verdicts) {
            this.verdicts = new ArrayDeque<>(List.of(verdicts));
        }

        @Override
        public ReviewVerdict review(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                                    DisputeDecision proposed) {
            reviewed.add(proposed);
            return verdicts.removeFirst();
        }
    }

    private static OrchestratorService service(DecisionEngine engine, DecisionReviewer critic,
                                               boolean reviewerEnabled) {
        return new OrchestratorService(
                dispute -> new EvidenceBundle("D-1", "TXN-1", "summary", List.of("SCA: FAILED"),
                        List.of("TXN-1"), List.of("get_transaction"), false, "evidence@vTest", NOW),
                (reasonCode, network) -> List.of("[visa-10.4#a] A passage."),
                engine, critic, new PromptSafetyGuard(), Clock.fixed(NOW, ZoneOffset.UTC),
                THRESHOLD, 3L, "orchestrator@vTest", reviewerEnabled);
    }

    // evidenceRefs wrong on purpose: the orchestrator's attestation must survive the review flow
    private static DisputeDecision decision(Decision verdict, String rationale) {
        return new DisputeDecision("D-1", verdict, 0.9, rationale, "10.4",
                List.of("[visa-10.4#a] A passage."), List.of("TXN-INVENTED"),
                "decision-llm@vTest", NOW);
    }

    private static Dispute dispute(long minorUnits) {
        return new Dispute("D-1", "TXN-1", "M-1", Network.VISA, "10.4",
                new Money(minorUnits, "EUR"), NOW, NOW.plus(Duration.ofDays(30)),
                "I never authorised this transaction.");
    }
}
