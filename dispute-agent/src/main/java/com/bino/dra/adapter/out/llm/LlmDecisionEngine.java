package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.llm.TokenBudgetAdvisor.TokenBudgetExceededException;
import com.bino.dra.adapter.out.support.Resources;
import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final String TOKEN_CAPPED_SUFFIX = "+token-capped";

    private static final Logger log = LoggerFactory.getLogger(LlmDecisionEngine.class);

    private final ChatClient chatClient;
    private final DraftValidator validator;
    private final Clock clock;
    private final String agentVersion;
    private final long maxTokens;
    private final String systemPrompt;

    public LlmDecisionEngine(
            ChatClient.Builder chatClientBuilder,
            DraftValidator validator,
            Clock clock,
            @Value("${dra.agent.decision-version}") String agentVersion,
            @Value("${dra.budget.decision-max-tokens}") long maxTokens,
            // Path derived from the pin, so the version is written once (see ADR-0003)
            @Value("classpath:prompts/decision/decide.${dra.prompts.decision}.md") Resource decisionPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.validator = validator;
        this.clock = clock;
        this.agentVersion = agentVersion;
        this.maxTokens = maxTokens;
        this.systemPrompt = Resources.text(decisionPrompt, "decision prompt");
    }

    @Override
    public DisputeDecision decide(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages) {
        String userMessage = DecisionPrompt.userMessage(dispute, evidence, rulePassages);
        Instant decidedAt = clock.instant();
        // One budget for the phase: the first call and its repair draw from the same allowance
        TokenBudgetAdvisor budget = new TokenBudgetAdvisor(maxTokens);

        try {
            DecisionDraft draft = ask(userMessage, budget);
            validator.validate(draft, rulePassages);
            logTokens(dispute, budget);
            return compose(dispute, draft, agentVersion, decidedAt);
        } catch (OutputValidationException invalidDraft) {
            return repairOnce(dispute, rulePassages, userMessage, invalidDraft.violations(), decidedAt, budget);
        } catch (JacksonException unparsableResponse) {
            return repairOnce(dispute, rulePassages, userMessage, List.of(NON_JSON_VIOLATION), decidedAt, budget);
        } catch (TokenBudgetExceededException budgetReached) {
            logTokens(dispute, budget);
            return escalateForTokenCap(dispute, rulePassages, budgetReached,
                    agentVersion + TOKEN_CAPPED_SUFFIX, decidedAt);
        }
    }

    private DisputeDecision repairOnce(Dispute dispute, List<String> rulePassages, String userMessage,
                                       List<String> violations, Instant decidedAt, TokenBudgetAdvisor budget) {
        try {
            DecisionDraft repaired = ask(DecisionPrompt.repairMessage(userMessage, violations), budget);
            validator.validate(repaired, rulePassages);
            logTokens(dispute, budget);
            return compose(dispute, repaired, agentVersion + REPAIRED_SUFFIX, decidedAt);
        } catch (OutputValidationException stillInvalid) {
            logTokens(dispute, budget);
            return escalateAfterFailedRepair(dispute, rulePassages, stillInvalid.violations(),
                    agentVersion + REPAIR_FAILED_SUFFIX, decidedAt);
        } catch (JacksonException stillProse) {
            logTokens(dispute, budget);
            return escalateAfterFailedRepair(dispute, rulePassages, List.of(NON_JSON_VIOLATION),
                    agentVersion + REPAIR_FAILED_SUFFIX, decidedAt);
        } catch (TokenBudgetExceededException budgetReached) {
            // The realistic path: a repair doubles the price of a case (ADR-0014), so it is the
            // first thing the budget refuses
            logTokens(dispute, budget);
            return escalateForTokenCap(dispute, rulePassages, budgetReached,
                    agentVersion + TOKEN_CAPPED_SUFFIX, decidedAt);
        }
    }

    private DecisionDraft ask(String userMessage, TokenBudgetAdvisor budget) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .advisors(budget)
                .call()
                .entity(DecisionDraft.class);
    }

    // Per-dispute correlation belongs in logs, never in a metric tag: one time series per dispute
    private void logTokens(Dispute dispute, TokenBudgetAdvisor budget) {
        log.info("tokens disputeId={} phase=decision consumed={} budget={}",
                dispute.disputeId(), budget.consumed(), budget.maxTokens());
    }

    // Names the budget: a reader must tell "the model was wrong" from "we stopped paying"
    static DisputeDecision escalateForTokenCap(Dispute dispute, List<String> rulePassages,
                                               TokenBudgetExceededException capped,
                                               String agentVersion, Instant decidedAt) {
        return DisputeDecision.escalation(
                dispute.disputeId(),
                "token budget reached",
                capped.consumed() + " tokens consumed of " + capped.maxTokens()
                        + ". The analysis was interrupted before it could be validated. Human review required.",
                dispute.reasonCode(),
                rulePassages,
                agentVersion,
                decidedAt);
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
