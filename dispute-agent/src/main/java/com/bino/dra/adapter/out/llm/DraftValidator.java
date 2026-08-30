package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.support.Text;
import com.bino.dra.domain.model.Decision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DraftValidator {

    private static final Pattern CHUNK_ID_PREFIX = Pattern.compile("^\\[([^\\]]+)]");
    private static final int PREVIEW_MAX = 60;

    private final Set<String> knownReasonCodes;

    public DraftValidator(@Value("${dra.validation.known-reason-codes}") Set<String> knownReasonCodes) {
        if (knownReasonCodes == null || knownReasonCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "dra.validation.known-reason-codes is empty: every decision would be rejected");
        }
        this.knownReasonCodes = Set.copyOf(knownReasonCodes);
    }

    public void validate(DecisionDraft draft, List<String> retrievedPassages) {
        List<String> provided = retrievedPassages == null ? List.of() : retrievedPassages;
        List<String> violations = new ArrayList<>();

        if (draft.decision() == null) {
            violations.add("missing decision");
        }
        if (draft.confidence() < 0.0 || draft.confidence() > 1.0) {
            violations.add("confidence out of [0,1]: " + draft.confidence());
        }
        if (draft.citedReasonCode() == null || !knownReasonCodes.contains(draft.citedReasonCode())) {
            violations.add("unknown citedReasonCode: " + draft.citedReasonCode());
        }

        if (draft.decision() != Decision.ESCALATE) {
            if (isEmpty(draft.evidenceRefs())) {
                violations.add("empty evidenceRefs (a decision without evidence is invalid)");
            }
            if (isEmpty(draft.citedRulePassages()) && !provided.isEmpty()) {
                violations.add("empty citedRulePassages while " + provided.size()
                        + " rule passages were provided");
            }
        }
        violations.addAll(unattestedCitations(draft.citedRulePassages(), provided));

        if (!violations.isEmpty()) {
            throw new OutputValidationException(violations);
        }
    }

    private static List<String> unattestedCitations(List<String> citations, List<String> provided) {
        if (isEmpty(citations)) {
            return List.of();
        }
        Set<String> providedIds = provided.stream()
                .map(DraftValidator::chunkId)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        return citations.stream()
                .filter(citation -> chunkId(citation).filter(providedIds::contains).isEmpty())
                .map(citation -> "unattested citedRulePassage (chunk id missing or unknown): "
                        + preview(citation))
                .toList();
    }

    static Optional<String> chunkId(String passage) {
        if (passage == null) {
            return Optional.empty();
        }
        Matcher prefix = CHUNK_ID_PREFIX.matcher(passage.stripLeading());
        return prefix.find() ? Optional.of(prefix.group(1)) : Optional.empty();
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private static String preview(String citation) {
        return Text.truncate(citation == null ? "(null)" : citation.strip(), PREVIEW_MAX);
    }
}
