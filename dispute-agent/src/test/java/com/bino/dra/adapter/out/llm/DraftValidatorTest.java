package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftValidatorTest {

    private final DraftValidator validator = new DraftValidator();

    private static DecisionDraft draft(Decision decision, double confidence, String reasonCode, List<String> evidenceRefs) {
        return new DecisionDraft(decision, confidence, "n/a", reasonCode, List.of("rule X"), evidenceRefs);
    }

    @Test
    void accepts_a_valid_draft() {
        DecisionDraft valid = draft(Decision.REPRESENT, 0.82, "10.4", List.of("TX-1"));

        assertThatCode(() -> validator.validate(valid)).doesNotThrowAnyException();
    }

    @Test
    void rejects_confidence_out_of_range() {
        DecisionDraft bad = draft(Decision.REPRESENT, 1.5, "10.4", List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(bad))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejects_unknown_reason_code() {
        DecisionDraft bad = draft(Decision.ACCEPT, 0.5, "99.9", List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(bad))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("citedReasonCode");
    }

    @Test
    void rejects_a_decision_without_evidence() {
        DecisionDraft bad = draft(Decision.REPRESENT, 0.7, "10.4", List.of());

        assertThatThrownBy(() -> validator.validate(bad))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("evidenceRefs");
    }
}
