package com.bino.dra.application.orchestration;

import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.DependencyUnavailableException;
import com.bino.dra.application.port.out.EvidenceGatherer;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final EvidenceGatherer evidenceGatherer;
    private final RuleRetriever ruleRetriever;
    private final DecisionEngine decisionEngine;
    private final PromptSafetyGuard guard;
    private final Clock clock;
    private final long escalationThresholdMinorUnits;
    private final Duration representmentMinRemaining;
    private final String agentVersion;

    public OrchestratorService(
            EvidenceGatherer evidenceGatherer,
            RuleRetriever ruleRetriever,
            DecisionEngine decisionEngine,
            PromptSafetyGuard guard,
            Clock clock,
            @Value("${dra.orchestrator.escalation-threshold-minor-units}") long escalationThresholdMinorUnits,
            @Value("${dra.orchestrator.representment-min-days-remaining}") long representmentMinDays,
            @Value("${dra.orchestrator.version}") String agentVersion) {
        this.evidenceGatherer = Objects.requireNonNull(evidenceGatherer);
        this.ruleRetriever = Objects.requireNonNull(ruleRetriever);
        this.decisionEngine = Objects.requireNonNull(decisionEngine);
        this.guard = Objects.requireNonNull(guard);
        this.clock = Objects.requireNonNull(clock);
        this.escalationThresholdMinorUnits = escalationThresholdMinorUnits;
        this.representmentMinRemaining = Duration.ofDays(representmentMinDays);
        this.agentVersion = Objects.requireNonNull(agentVersion);
    }

    public DisputeDecision resolve(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");

        Optional<String> unsafeField = guard.reject(dispute);
        if (unsafeField.isPresent()) {
            return escalateWithoutModel(dispute, List.of(),
                    "cardholder data detected in the " + unsafeField.get());
        }
        Dispute safe = guard.neutralise(dispute);

        EvidenceBundle bundle;
        // rulePassages holds whatever was retrieved before the outage: empty if RAG never ran
        List<String> rulePassages = List.of();
        try {
            bundle = evidenceGatherer.gather(safe);
            rulePassages = ruleRetriever.retrieveRulePassages(safe.reasonCode(), safe.network());
        } catch (DependencyUnavailableException outage) {
            return escalateForOutage(safe, rulePassages, outage);
        }

        if (bundle.isEmpty()) {
            return escalateWithoutModel(safe, rulePassages, "no attested evidence from the tools");
        }
        // The decision prompt reads an empty file as ACCEPT: a lost narrative must reach a human
        if (!bundle.hasNarrative()) {
            return escalateWithoutModel(safe, rulePassages,
                    "evidence gathering interrupted (" + bundle.agentVersion() + ")")
                    .withEvidenceRefs(bundle.evidenceRefs());
        }

        DisputeDecision proposed;
        try {
            proposed = decisionEngine.decide(safe, bundle, rulePassages);
        } catch (DependencyUnavailableException outage) {
            return escalateForOutage(safe, rulePassages, outage);
        }

        return applyGovernance(safe, bundle, proposed);
    }

    // The message names the dependency and the detail: "revoked key" and "503" call for different actions
    private DisputeDecision escalateForOutage(Dispute dispute, List<String> rulePassages,
                                              DependencyUnavailableException outage) {
        log.warn("Dependency outage on dispute {}: {}", dispute.disputeId(), outage.getMessage(), outage);
        return escalateWithoutModel(dispute, rulePassages, outage.getMessage());
    }

    private DisputeDecision escalateWithoutModel(Dispute dispute, List<String> rulePassages, String reason) {
        return DisputeDecision.escalation(
                dispute.disputeId(),
                reason,
                "No model was consulted on this dispute. Human review required.",
                dispute.reasonCode(),
                rulePassages,
                agentVersion,
                clock.instant());
    }

    private DisputeDecision applyGovernance(Dispute dispute, EvidenceBundle bundle, DisputeDecision proposed) {
        DisputeDecision attested = proposed.withEvidenceRefs(bundle.evidenceRefs());

        return escalationReason(dispute)
                .map(attested::escalatedBecause)
                .orElse(attested);
    }

    private Optional<String> escalationReason(Dispute dispute) {
        Optional<String> deadline = representmentDeadlineReason(dispute);
        if (deadline.isPresent()) {
            return deadline;
        }
        if (dispute.disputedAmount().minorUnits() > escalationThresholdMinorUnits) {
            return Optional.of("disputed amount above the " + escalationThresholdMinorUnits
                    + " minor units threshold");
        }
        return Optional.empty();
    }

    private Optional<String> representmentDeadlineReason(Dispute dispute) {
        if (dispute.representmentDueBy() == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(clock.instant(), dispute.representmentDueBy());
        if (!remaining.isPositive()) {
            return Optional.of("representment deadline expired on " + dispute.representmentDueBy());
        }
        if (remaining.compareTo(representmentMinRemaining) < 0) {
            return Optional.of("representment deadline too close - due " + dispute.representmentDueBy()
                    + ", less than " + representmentMinRemaining.toDays() + " days to build the case");
        }
        return Optional.empty();
    }
}
