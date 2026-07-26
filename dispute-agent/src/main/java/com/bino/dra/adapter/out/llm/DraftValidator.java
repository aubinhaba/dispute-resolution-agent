package com.bino.dra.adapter.out.llm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DraftValidator {

    private static final Set<String> KNOWN_REASON_CODES = Set.of(
            "10.4", "13.1", "13.3",   // Visa
            "4837", "4855"            // Mastercard
    );

    public void validate(DecisionDraft draft) {
        List<String> violations = new ArrayList<>();

        if (draft.decision() == null) {
            violations.add("missing decision");
        }
        if (draft.confidence() < 0.0 || draft.confidence() > 1.0) {
            violations.add("confidence out of [0,1]: " + draft.confidence());
        }
        if (draft.citedReasonCode() == null || !KNOWN_REASON_CODES.contains(draft.citedReasonCode())) {
            violations.add("unknown citedReasonCode: " + draft.citedReasonCode());
        }
        if (draft.evidenceRefs() == null || draft.evidenceRefs().isEmpty()) {
            violations.add("empty evidenceRefs (a decision without evidence is invalid)");
        }

        if (!violations.isEmpty()) {
            throw new OutputValidationException(violations);
        }
    }
}
