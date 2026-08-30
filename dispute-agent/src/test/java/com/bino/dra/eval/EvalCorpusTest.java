package com.bino.dra.eval;

import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.orchestration.OrchestratorService;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCorpusTest {

    private static final long THRESHOLD = 100_000L;
    private static final long MARGIN_DAYS = 3L;

    private static final Set<String> CATALOGUE = Set.of(
            "10.4", "11.3", "12.6", "13.1", "13.2", "13.3", "4808", "4834", "4837", "4853", "4855");

    private static final List<EvalScenario> FUNCTIONAL = EvalCorpusLoader.loadFunctionalCases();
    private static final List<AdversarialScenario> ADVERSARIAL = EvalCorpusLoader.loadAdversarialCases();

    @Nested
    @DisplayName("Corpus integrity")
    class Integrity {
        @Test
        @DisplayName("20 functional and 10 adversarial cases, every id unique")
        void bothCorporaHaveTheAnnouncedSize() {
            assertThat(FUNCTIONAL).hasSize(20);
            assertThat(ADVERSARIAL).hasSize(10);

            assertThat(FUNCTIONAL).extracting(EvalScenario::id).doesNotHaveDuplicates();
            assertThat(ADVERSARIAL).extracting(AdversarialScenario::id).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("the two corpora are DISJOINT - an adversarial case never counts towards accuracy")
        void theCorporaAreDisjoint() {
            Set<String> functionalIds = FUNCTIONAL.stream()
                    .map(EvalScenario::id).collect(Collectors.toSet());

            assertThat(ADVERSARIAL).extracting(AdversarialScenario::id)
                    .allSatisfy(id -> assertThat(id).startsWith("ADV-"))
                    .doesNotContainAnyElementsOf(functionalIds);
            assertThat(functionalIds).allSatisfy(id -> assertThat(id).startsWith("EVAL-"));
        }

        @Test
        @DisplayName("every ground truth is complete and its reason code is in the catalogue")
        void everyGroundTruthIsUsable() {
            assertThat(FUNCTIONAL).allSatisfy(s -> {
                assertThat(s.groundTruth().expectedDecision()).isNotNull();
                assertThat(s.groundTruth().expectedReasonCode()).isIn(CATALOGUE);
                assertThat(s.why()).isNotBlank();
                assertThat(s.dispute().issuerClaim()).isNotBlank();
            });
        }

        @Test
        @DisplayName("all three decisions and both networks are represented")
        void theCorpusCoversAllDecisionsAndBothNetworks() {
            assertThat(FUNCTIONAL).extracting(s -> s.groundTruth().expectedDecision())
                    .contains(Decision.ACCEPT, Decision.REPRESENT, Decision.ESCALATE);
            assertThat(FUNCTIONAL).extracting(s -> s.dispute().network())
                    .contains(Network.VISA, Network.MASTERCARD);
        }

        @Test
        @DisplayName("no non-deadline case sits near the margin - it would escalate for the wrong reason")
        void casesThatDoNotMeasureTheDeadlineStayFarFromIt() {
            Instant now = Instant.now();

            assertThat(FUNCTIONAL)
                    .filteredOn(s -> !s.id().equals("EVAL-015") && !s.id().equals("EVAL-016"))
                    .allSatisfy(s -> assertThat(
                            Duration.between(now, s.dispute().representmentDueBy()))
                            .isGreaterThan(Duration.ofDays(MARGIN_DAYS)));
        }
    }

    @Nested
    @DisplayName("Deterministic paths - measured with no model")
    class DeterministicPaths {
        @Test
        @DisplayName("the 5 deterministic cases escalate, AFTER the model has been consulted")
        void deterministicCasesEscalateAndKeepTheAnalysis() {
            List<EvalScenario> deterministic = FUNCTIONAL.stream()
                    .filter(EvalScenario::deterministic).toList();
            assertThat(deterministic).hasSize(5);

            for (EvalScenario scenario : deterministic) {
                StubEngine engine = new StubEngine();
                DisputeDecision decision = orchestrator(engine).resolve(scenario.dispute());

                assertThat(decision.decision())
                        .as("case %s - %s", scenario.id(), scenario.why())
                        .isEqualTo(Decision.ESCALATE);
                assertThat(decision.rationale()).startsWith("[AUTOMATIC ESCALATION");
                assertThat(decision.rationale()).containsAnyOf("deadline", "amount");

                assertThat(engine.called)
                        .as("case %s: the model analysis must stay in the file (ADR-0012)", scenario.id())
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Input guardrail - measured with no model")
    class PanGuardrail {
        private final PromptSafetyGuard guard = new PromptSafetyGuard();

        @Test
        @DisplayName("every PAN case is rejected, and the named field is the right one")
        void pansAreRejectedWithTheRightField() {
            List<AdversarialScenario> withPan = ADVERSARIAL.stream()
                    .filter(AdversarialScenario::expectsPanRejection).toList();
            assertThat(withPan).hasSize(4);

            for (AdversarialScenario scenario : withPan) {
                assertThat(guard.reject(scenario.dispute()))
                        .as("case %s - %s", scenario.id(), scenario.why())
                        .contains(scenario.panField());
            }
        }

        @Test
        @DisplayName("negative control: a 16-digit order number is NOT a PAN")
        void theNegativeControlIsNotRejected() {
            AdversarialScenario control = ADVERSARIAL.stream()
                    .filter(s -> !s.expectsPanRejection() && !s.carriesCanary())
                    .findFirst().orElseThrow();

            assertThat(guard.reject(control.dispute())).isEmpty();
        }

        @Test
        @DisplayName("canary cases really carry their canary - fixture integrity")
        void canariesArePresentInTheClaim() {
            List<AdversarialScenario> withCanary = ADVERSARIAL.stream()
                    .filter(AdversarialScenario::carriesCanary).toList();
            assertThat(withCanary).hasSize(5);

            assertThat(withCanary).allSatisfy(s ->
                    assertThat(s.dispute().issuerClaim()).contains(s.canary()));
        }
    }

    private static OrchestratorService orchestrator(StubEngine engine) {
        return new OrchestratorService(
                EvalCorpusTest::attestedBundle,
                (reasonCode, network) -> List.of("[visa-10.4#liability-shift] Rule passage."),
                engine,
                new PromptSafetyGuard(),
                Clock.systemUTC(),
                THRESHOLD, MARGIN_DAYS, "orchestrator@v1.0.0");
    }

    private static EvidenceBundle attestedBundle(Dispute dispute) {
        return new EvidenceBundle(
                dispute.disputeId(), dispute.transactionId(),
                "Transaction authenticated, checks consistent.",
                List.of("SCA: AUTHENTICATED"),
                List.of(dispute.transactionId()),
                List.of("get_transaction"),
                false,
                "evidence-llm@v1.0.0",
                Instant.now());
    }

    private static final class StubEngine implements DecisionEngine {
        private boolean called;

        @Override
        public DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages) {
            this.called = true;
            return new DisputeDecision(
                    dispute.disputeId(), Decision.REPRESENT, 0.9,
                    "Strong file, representment recommended.",
                    dispute.reasonCode(),
                    rulePassages,
                    evidence.evidenceRefs(),
                    "decision-llm@v1.2.0",
                    Instant.now());
        }
    }
}
