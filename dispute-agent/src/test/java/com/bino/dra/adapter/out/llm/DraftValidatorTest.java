package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftValidatorTest {

    private static final String PASSAGE_10_4 =
            "[visa-10.4#liability-shift] Fraud - Card-Absent Environment - Liability shift: "
                    + "Where the transaction was successfully authenticated through 3-D Secure...";
    private static final String PASSAGE_3DS =
            "[shared-3ds#outcome] 3-D Secure - Outcome: An authenticated transaction shifts...";
    private static final List<String> PROVIDED = List.of(PASSAGE_10_4, PASSAGE_3DS);

    private final DraftValidator validator = new DraftValidator(
            Set.of("10.4", "11.3", "12.6", "13.1", "13.2", "13.3", "4808", "4834", "4837", "4853", "4855"));

    private static DecisionDraft draft(Decision decision, double confidence, String reasonCode,
                                       List<String> citations, List<String> evidenceRefs) {
        return new DecisionDraft(decision, confidence, "n/a", reasonCode, citations, evidenceRefs);
    }

    private static DecisionDraft valid() {
        return draft(Decision.REPRESENT, 0.82, "10.4", List.of(PASSAGE_10_4), List.of("TX-1"));
    }

    @Test
    void accepts_a_valid_draft() {
        assertThatCode(() -> validator.validate(valid(), PROVIDED)).doesNotThrowAnyException();
    }

    @Test
    void rejects_confidence_out_of_range() {
        DecisionDraft bad = draft(Decision.REPRESENT, 1.5, "10.4", List.of(PASSAGE_10_4), List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(bad, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejects_a_reason_code_outside_the_catalogue() {
        DecisionDraft bad = draft(Decision.ACCEPT, 0.5, "99.9", List.of(PASSAGE_10_4), List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(bad, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("citedReasonCode");
    }

    @Test
    void accepts_the_codes_the_hardcoded_catalogue_used_to_miss() {
        assertThatCode(() -> validator.validate(
                draft(Decision.ACCEPT, 0.5, "11.3", List.of(PASSAGE_3DS), List.of("TX-1")), PROVIDED))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(
                draft(Decision.ACCEPT, 0.5, "4853", List.of(PASSAGE_3DS), List.of("TX-1")), PROVIDED))
                .doesNotThrowAnyException();
    }

    @Test
    void an_empty_catalogue_is_a_configuration_error_not_a_permissive_default() {
        assertThatThrownBy(() -> new DraftValidator(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_decision_without_evidence() {
        DecisionDraft bad = draft(Decision.REPRESENT, 0.7, "10.4", List.of(PASSAGE_10_4), List.of());

        assertThatThrownBy(() -> validator.validate(bad, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("evidenceRefs");
    }

    @Test
    void rejects_a_decision_citing_no_rule_while_rules_were_provided() {
        DecisionDraft bad = draft(Decision.REPRESENT, 0.7, "10.4", List.of(), List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(bad, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("empty citedRulePassages");
    }

    @Test
    void exempts_ESCALATE_from_both_audit_trail_rules() {
        DecisionDraft escalation = draft(Decision.ESCALATE, 0.0, "10.4", List.of(), List.of());

        assertThatCode(() -> validator.validate(escalation, PROVIDED)).doesNotThrowAnyException();
    }

    @Test
    void requires_no_citation_when_the_rag_provided_nothing() {
        DecisionDraft noRule = draft(Decision.REPRESENT, 0.7, "10.4", List.of(), List.of("TX-1"));

        assertThatCode(() -> validator.validate(noRule, List.of())).doesNotThrowAnyException();
    }

    @Test
    void accepts_a_citation_that_kept_its_chunk_id() {
        DecisionDraft good = draft(Decision.REPRESENT, 0.9, "10.4",
                List.of("[visa-10.4#liability-shift] Where the transaction was authenticated..."),
                List.of("TX-1"));

        assertThatCode(() -> validator.validate(good, PROVIDED)).doesNotThrowAnyException();
    }

    @Test
    void rejects_a_citation_that_lost_its_chunk_id_crossing_the_model() {
        DecisionDraft unanchored = draft(Decision.REPRESENT, 0.9, "10.4",
                List.of("Fraud - Card-Absent Environment - Liability shift: Where the transaction..."),
                List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(unanchored, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("unattested");
    }

    @Test
    void rejects_a_chunk_id_absent_from_the_retrieved_set() {
        DecisionDraft invented = draft(Decision.REPRESENT, 0.9, "10.4",
                List.of("[visa-13.1#delivery-proof] Merchandise not received - Proof of delivery..."),
                List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(invented, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("unattested");
    }

    @Test
    void rejects_a_chunk_id_found_anywhere_but_in_leading_position() {
        DecisionDraft midText = draft(Decision.REPRESENT, 0.9, "10.4",
                List.of("Under rule [visa-10.4#liability-shift], liability shifts."),
                List.of("TX-1"));

        assertThatThrownBy(() -> validator.validate(midText, PROVIDED))
                .isInstanceOf(OutputValidationException.class)
                .hasMessageContaining("unattested");
    }

    @Test
    void lists_every_violation_because_the_repair_message_needs_them_all() {
        DecisionDraft broken = draft(Decision.REPRESENT, 2.0, "99.9",
                List.of("citation without identifier"), List.of());

        assertThatThrownBy(() -> validator.validate(broken, PROVIDED))
                .isInstanceOfSatisfying(OutputValidationException.class,
                        e -> assertThat(e.violations()).hasSize(4));
    }
}
