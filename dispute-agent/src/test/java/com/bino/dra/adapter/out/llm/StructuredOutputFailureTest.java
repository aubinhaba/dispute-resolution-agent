package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.agent.EvidenceDraft;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputFailureTest {

    private static final String PROSE = """
            Based on my analysis of the transaction, the evidence strongly supports
            representment. The 3DS authentication succeeded and AVS matched.
            """;

    @Test
    void a_prose_answer_raises_a_JacksonException_and_not_a_wrapped_exception() {
        BeanOutputConverter<EvidenceDraft> converter = new BeanOutputConverter<>(EvidenceDraft.class);

        assertThatThrownBy(() -> converter.convert(PROSE))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void the_exception_is_unchecked_which_is_why_it_crossed_the_port_unnoticed() {
        assertThatThrownBy(() -> new BeanOutputConverter<>(EvidenceDraft.class).convert(PROSE))
                .isInstanceOf(RuntimeException.class);
    }
}
