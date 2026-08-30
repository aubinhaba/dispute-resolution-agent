package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.support.Resources;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class LlmDecisionEngine implements DecisionEngine {

    private static final String NON_JSON_VIOLATION =
            "the answer was not usable JSON: reply ONLY with a JSON object matching the schema, "
                    + "with no introduction and no markdown fence";

    private static final String REPAIRED_SUFFIX = "+repaired";
    private static final String REPAIR_FAILED_SUFFIX = "+repair-failed";

    private final ChatClient chatClient;
    private final DraftValidator validator;
    private final Clock clock;
    private final String agentVersion;
    private final String systemPrompt;

    public LlmDecisionEngine(
            ChatClient.Builder chatClientBuilder,
            DraftValidator validator,
            Clock clock,
            @Value("${dra.agent.decision-version}") String agentVersion,
            @Value("classpath:prompts/decision/decide.v1.2.0.md") Resource decisionPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.validator = validator;
        this.clock = clock;
        this.agentVersion = agentVersion;
        this.systemPrompt = Resources.text(decisionPrompt, "decision prompt");
    }

    @Override
    public DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages) {
        String userMessage = DecisionPrompt.userMessage(dispute, evidence, rulePassages);
        Instant decidedAt = clock.instant();

        try {
            DecisionDraft draft = ask(userMessage);
            validator.validate(draft, rulePassages);
            return compose(dispute, draft, agentVersion, decidedAt);
        } catch (OutputValidationException invalidDraft) {
            return repairOnce(dispute, rulePassages, userMessage, invalidDraft.violations(), decidedAt);
        } catch (JacksonException unparsableResponse) {
            return repairOnce(dispute, rulePassages, userMessage, List.of(NON_JSON_VIOLATION), decidedAt);
        }
    }

    private DisputeDecision repairOnce(Dispute dispute, List<String> rulePassages, String userMessage,
                                       List<String> violations, Instant decidedAt) {
        try {
            DecisionDraft repaired = ask(DecisionPrompt.repairMessage(userMessage, violations));
            validator.validate(repaired, rulePassages);
            return compose(dispute, repaired, agentVersion + REPAIRED_SUFFIX, decidedAt);
        } catch (OutputValidationException stillInvalid) {
            return escalateAfterFailedRepair(dispute, rulePassages, stillInvalid.violations(),
                    agentVersion + REPAIR_FAILED_SUFFIX, decidedAt);
        } catch (JacksonException stillProse) {
            return escalateAfterFailedRepair(dispute, rulePassages, List.of(NON_JSON_VIOLATION),
                    agentVersion + REPAIR_FAILED_SUFFIX, decidedAt);
        }
    }

    private DecisionDraft ask(String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .entity(DecisionDraft.class);
    }

    static DisputeDecision escalateAfterFailedRepair(Dispute dispute, List<String> rulePassages,
                                                     List<String> violations, String agentVersion,
                                                     Instant decidedAt) {
        return DisputeDecision.escalation(
                dispute.disputeId(),
                "invalid model output after repair",
                "Persistent violations: " + String.join(" | ", violations)
                        + ". No usable decision could be produced. Human review required.",
                dispute.reasonCode(),
                rulePassages,
                agentVersion,
                decidedAt);
    }

    static DisputeDecision compose(Dispute dispute, DecisionDraft draft, String agentVersion, Instant decidedAt) {
        return new DisputeDecision(
                dispute.disputeId(),
                draft.decision(),
                draft.confidence(),
                draft.rationale(),
                draft.citedReasonCode(),
                draft.citedRulePassages(),
                draft.evidenceRefs(),
                agentVersion,
                decidedAt
        );
    }
}
