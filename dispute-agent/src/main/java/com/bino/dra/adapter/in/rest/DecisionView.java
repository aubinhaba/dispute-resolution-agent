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

    private static final String DETERMINISTIC_PREFIX = "orchestrator@";

    public static DecisionView from(DisputeDecision d) {
        // One question labels four fields: did a model intervene? The agent version says so
        Provenance verdict = isDeterministic(d.agentVersion()) ? Provenance.ATTESTED : Provenance.MODEL;

        return new DecisionView(
                new Provenanced<>(d.decision(), verdict),
                new Provenanced<>(d.confidence(), verdict),
                new Provenanced<>(d.rationale(), verdict),
                new Provenanced<>(d.citedReasonCode(), verdict),
                Provenanced.attested(d.citedRulePassages()),
                Provenanced.attested(d.evidenceRefs()),
                Provenanced.attested(d.agentVersion()),
                Provenanced.attested(d.decidedAt()));
    }

    private static boolean isDeterministic(String agentVersion) {
        return agentVersion != null && agentVersion.startsWith(DETERMINISTIC_PREFIX);
    }
}
