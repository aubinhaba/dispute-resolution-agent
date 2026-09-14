package com.bino.dra.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvalGatePolicyTest {

    private static final String PINNED = "decision-llm@v1.2.0";

    @Test
    void a_conforming_report_passes() {
        assertThat(EvalGatePolicy.evaluate(conformingReport(), PINNED).passed()).isTrue();
    }

    @Test
    void a_decision_accuracy_below_the_floor_fails_and_says_so() {
        EvalReport drop = report(0.70, 1.0, 1.0, 1.0, 1.0, Set.of(PINNED));

        EvalGatePolicy.Verdict verdict = EvalGatePolicy.evaluate(drop, PINNED);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.contains("decisionAccuracy"));
    }

    @Test
    void a_single_unblocked_injection_fails_with_no_tolerance() {
        EvalReport leak = report(0.90, 1.0, 8.0 / 9.0, 1.0, 1.0, Set.of(PINNED));

        assertThat(EvalGatePolicy.evaluate(leak, PINNED).passed()).isFalse();
    }

    // Without this floor the drift would stay invisible until the invoice: every other metric
    // remains perfect while the price per dispute doubles
    @Test
    void a_collapsing_first_pass_rate_fails_even_when_everything_else_is_perfect() {
        EvalReport repairs = report(1.0, 1.0, 1.0, 1.0, 0.50, Set.of(PINNED));

        EvalGatePolicy.Verdict verdict = EvalGatePolicy.evaluate(repairs, PINNED);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.contains("firstPassAttestationRate"));
    }

    @Test
    void a_report_measuring_ANOTHER_version_than_the_pin_fails() {
        EvalReport otherVersion = report(1.0, 1.0, 1.0, 1.0, 1.0, Set.of("decision-llm@v1.1.0"));

        EvalGatePolicy.Verdict verdict = EvalGatePolicy.evaluate(otherVersion, PINNED);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violations()).anyMatch(v -> v.contains("v1.1.0"));
    }

    private static EvalReport conformingReport() {
        return report(0.85, 1.0, 1.0, 1.0, 1.0, Set.of(PINNED));
    }

    private static EvalReport report(double decisions, double reasonCodes, double injections,
                                     double attestation, double firstPass,
                                     Set<String> agentVersionsObserved) {
        return new EvalReport(decisions, reasonCodes, injections, attestation, firstPass,
                25, 0, 0, agentVersionsObserved, "claude-haiku-4-5", List.of());
    }
}
