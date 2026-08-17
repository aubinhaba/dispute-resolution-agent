package com.bino.dra.application.orchestration;

import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.EvidenceGatherer;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrchestratorService {

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
        this.agentVersion = agentVersion;
    }

    public DisputeDecision resolve(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");

        // A detected PAN is rejected, not masked: rewriting cardholder data would assume our mask is right
        Optional<String> unsafeField = guard.reject(dispute);
        if (unsafeField.isPresent()) {
            return escalateWithoutModel(dispute, List.of(),
                    "cardholder data detected in the " + unsafeField.get());
        }
        Dispute safe = guard.neutralise(dispute);

        EvidenceBundle bundle = evidenceGatherer.gather(safe);
        List<String> rulePassages =
                ruleRetriever.retrieveRulePassages(safe.reasonCode(), safe.network());

        // No model call on an empty bundle: there is nothing to reason about
        if (bundle.isEmpty()) {
            return escalateWithoutModel(safe, rulePassages, "no attested evidence from the tools");
        }

        DisputeDecision proposed = decisionEngine.decide(safe, bundle, rulePassages);

        return applyGovernance(safe, bundle, proposed);
    }

    // The reason never echoes an input field: on the PAN path that would leak what was just refused
    private DisputeDecision escalateWithoutModel(Dispute dispute, List<String> rulePassages, String reason) {
        return new DisputeDecision(
                dispute.disputeId(),
                Decision.ESCALATE,
                0.0,
                "[AUTOMATIC ESCALATION - " + reason + "] No model was consulted on this dispute. "
                        + "Human review required.",
                dispute.reasonCode(),
                rulePassages,
                List.of(),
                agentVersion,
                clock.instant());
    }

    private DisputeDecision applyGovernance(Dispute dispute, EvidenceBundle bundle, DisputeDecision proposed) {
        Optional<String> escalation = escalationReason(dispute);

        return new DisputeDecision(
                proposed.disputeId(),
                escalation.isPresent() ? Decision.ESCALATE : proposed.decision(),
                proposed.confidence(),
                escalation.map(reason -> "[AUTOMATIC ESCALATION - " + reason + "] " + proposed.rationale())
                        .orElseGet(proposed::rationale),
                proposed.citedReasonCode(),
                // Model-selected but validated upstream: each citation carries a retrieved chunk id
                proposed.citedRulePassages(),
                // Attested from the observed tool trail, not from what the model claimed to have used
                bundle.evidenceRefs(),
                proposed.agentVersion(),
                proposed.decidedAt());
    }

    // Both rules override the model without skipping the call; deadline wins over amount (ADR-0012)
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
