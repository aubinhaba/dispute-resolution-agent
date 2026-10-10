package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.support.Resources;
import com.bino.dra.application.guard.PromptSafetyGuard;
import com.bino.dra.application.port.out.DecisionReviewer;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.ReviewVerdict;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.util.List;

@Component
public class LlmDecisionReviewer implements DecisionReviewer {

    // Objections travel into the generator's prompt: a model-to-model channel, bounded like any untrusted input
    static final int MAX_OBJECTIONS = 5;
    static final int OBJECTION_MAX_CHARS = 300;

    private final ChatClient chatClient;
    private final String systemPrompt;

    public LlmDecisionReviewer(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/review/review.${dra.prompts.review}.md") Resource reviewPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.systemPrompt = Resources.text(reviewPrompt, "review prompt");
    }

    // No token budget on purpose: it refuses the NEXT turn, and a review is a single call (see ADR-0024)
    @Override
    public ReviewVerdict review(Dispute dispute, EvidenceBundle evidence, List<String> rulePassages,
                                DisputeDecision proposed) {
        try {
            ReviewDraft draft = chatClient.prompt()
                    .system(systemPrompt)
                    .user(DecisionPrompt.reviewMessage(dispute, evidence, rulePassages, proposed))
                    .call()
                    .entity(ReviewDraft.class);
            return toVerdict(draft);
        } catch (JacksonException unparsableResponse) {
            return new ReviewVerdict.Inconclusive("unparsable reviewer answer");
        }
    }

    // Objections outrank the boolean: a "coherent" that comes with a reproach contradicts itself
    static ReviewVerdict toVerdict(ReviewDraft draft) {
        if (draft == null) {
            return new ReviewVerdict.Inconclusive("empty reviewer answer");
        }
        List<String> objections = bounded(draft.objections());
        if (!objections.isEmpty()) {
            return new ReviewVerdict.Incoherent(objections);
        }
        return draft.coherent()
                ? new ReviewVerdict.Coherent()
                : new ReviewVerdict.Inconclusive("incoherence declared without a usable objection");
    }

    private static List<String> bounded(List<String> objections) {
        if (objections == null) {
            return List.of();
        }
        return objections.stream()
                .filter(o -> o != null && !o.isBlank())
                // An objection echoing the issuer claim must not forge a section of the revision prompt
                .map(o -> PromptSafetyGuard.neutraliseDelimiters(o.strip()))
                .map(o -> o.length() <= OBJECTION_MAX_CHARS ? o : o.substring(0, OBJECTION_MAX_CHARS))
                .limit(MAX_OBJECTIONS)
                .toList();
    }
}
