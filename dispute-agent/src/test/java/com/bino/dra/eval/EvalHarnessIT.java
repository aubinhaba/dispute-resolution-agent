package com.bino.dra.eval;

import com.bino.dra.application.orchestration.OrchestratorService;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// This file only measures; the rule lives in EvalGatePolicy, which is verifiable without a key
@NoDatabase
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class EvalHarnessIT {

    private static final Path REPORT = Path.of("target", "eval-report.json");

    @Autowired
    private OrchestratorService orchestrator;

    // Derived from the pin: the report must name what it measured
    @Value("${dra.agent.decision-version}")
    private String pinnedVersion;

    @Value("${spring.ai.anthropic.chat.options.model}")
    private String model;

    @Test
    void measures_the_whole_system_over_the_thirty_labelled_cases() {
        List<EvalScenario> functional = EvalCorpusLoader.loadFunctionalCases();
        List<AdversarialScenario> adversarial = EvalCorpusLoader.loadAdversarialCases();

        List<DisputeDecision> decisions = new ArrayList<>();
        for (EvalScenario scenario : functional) {
            decisions.add(orchestrator.resolve(scenario.dispute()));
        }
        List<DisputeDecision> adversarialDecisions = new ArrayList<>();
        for (AdversarialScenario scenario : adversarial) {
            adversarialDecisions.add(orchestrator.resolve(scenario.dispute()));
        }

        EvalReport report =
                EvalReport.compute(functional, decisions, adversarial, adversarialDecisions, model);
        EvalGatePolicy.Verdict verdict = EvalGatePolicy.evaluate(report, pinnedVersion);
        report.write(REPORT, verdict);
        System.out.println(report.summary());
        report.failures().forEach(System.out::println);

        assertThat(verdict.passed())
                .as("eval gate - violations: %s; failures: %s", verdict.violations(), report.failures())
                .isTrue();
        assertThat(REPORT).exists();
    }
}
