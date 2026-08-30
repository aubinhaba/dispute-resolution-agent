package com.bino.dra.adapter.in.rest;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.DisputeDecision;

import java.time.Instant;
import java.util.List;

public record DecisionView(
        Provenanced<Decision> decision,
        Provenanced<Double> confidence,
        Provenanced<String> rationale,
        Provenanced<String> citedReasonCode,
        Provenanced<List<String>> citedRulePassages,
        Provenanced<List<String>> evidenceRefs,
        Provenanced<String> agentVersion,
        Provenanced<Instant> decidedAt) {
    // Must match dra.orchestrator.version in application.yml: renaming that value silently
    // relabels every deterministic escalation as MODEL
    private static final String DETERMINISTIC_PREFIX = "orchestrator@";

    public static DecisionView from(DisputeDecision d) {
        boolean deterministic = isDeterministic(d.agentVersion());

        return new DecisionView(
                label(d.decision(), deterministic),
                label(d.confidence(), deterministic),
                label(d.rationale(), deterministic),
                label(d.citedReasonCode(), deterministic),
                Provenanced.attested(d.citedRulePassages()),
                Provenanced.attested(d.evidenceRefs()),
                Provenanced.attested(d.agentVersion()),
                Provenanced.attested(d.decidedAt()));
    }

    private static <T> Provenanced<T> label(T value, boolean deterministic) {
        return deterministic ? Provenanced.attested(value) : Provenanced.model(value);
    }

    private static boolean isDeterministic(String agentVersion) {
        return agentVersion != null && agentVersion.startsWith(DETERMINISTIC_PREFIX);
    }
}
