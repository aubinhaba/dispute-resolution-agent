package com.bino.dra.application.orchestration;

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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrchestratorService {

    private final EvidenceGatherer evidenceGatherer;
    private final RuleRetriever ruleRetriever;
    private final DecisionEngine decisionEngine;
    private final Clock clock;
    private final long escalationThresholdMinorUnits;
    private final String agentVersion;

    public OrchestratorService(
            EvidenceGatherer evidenceGatherer,
            RuleRetriever ruleRetriever,
            DecisionEngine decisionEngine,
            Clock clock,
            @Value("${dra.orchestrator.escalation-threshold-minor-units}") long escalationThresholdMinorUnits,
            @Value("${dra.orchestrator.version}") String agentVersion) {
        this.evidenceGatherer = Objects.requireNonNull(evidenceGatherer);
        this.ruleRetriever = Objects.requireNonNull(ruleRetriever);
        this.decisionEngine = Objects.requireNonNull(decisionEngine);
        this.clock = Objects.requireNonNull(clock);
        this.escalationThresholdMinorUnits = escalationThresholdMinorUnits;
        this.agentVersion = agentVersion;
    }

    public DisputeDecision resolve(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");

        EvidenceBundle bundle = evidenceGatherer.gather(dispute);
        List<String> rulePassages =
                ruleRetriever.retrieveRulePassages(dispute.reasonCode(), dispute.network());

        // No model call on an empty bundle: it would return no evidenceRefs, which DraftValidator rejects
        if (bundle.isEmpty()) {
            return escalateWithoutModel(dispute, rulePassages);
        }

        DisputeDecision proposed = decisionEngine.decide(dispute, bundle, rulePassages);

        return applyGovernance(dispute, bundle, proposed);
    }

    private DisputeDecision escalateWithoutModel(Dispute dispute, List<String> rulePassages) {
        return new DisputeDecision(
                dispute.disputeId(),
                Decision.ESCALATE,
                0.0,
                "[AUTOMATIC ESCALATION - no attested evidence] The investigation produced no verifiable "
                        + "reference for transaction " + dispute.transactionId() + ". No model was consulted: "
                        + "there was no evidence to analyse. Human review required.",
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
                // citedRulePassages stay model-selected: unlike evidenceRefs they are not attested yet
                proposed.citedRulePassages(),
                // Attested from the observed tool trail, not from what the model claimed to have used
                bundle.evidenceRefs(),
                proposed.agentVersion(),
                proposed.decidedAt());
    }

    // The rule overrides the model verdict and deliberately does not skip the call (see ADR-0012)
    private Optional<String> escalationReason(Dispute dispute) {
        if (dispute.disputedAmount().minorUnits() > escalationThresholdMinorUnits) {
            return Optional.of("disputed amount above the " + escalationThresholdMinorUnits
                    + " minor units threshold");
        }
        return Optional.empty();
    }
}
