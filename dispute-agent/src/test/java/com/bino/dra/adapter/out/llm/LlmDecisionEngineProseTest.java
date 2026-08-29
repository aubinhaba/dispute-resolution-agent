package com.bino.dra.adapter.out.llm;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// .entity() raises BEFORE any validation, and unchecked: it crossed the port (see ADR-0014)
class LlmDecisionEngineProseTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private static final String PROSE = """
            Based on my analysis, I recommend representment because 3DS succeeded.
            """;

    private static final String VALID_JSON = """
            {"decision":"ACCEPT","confidence":0.7,"rationale":"Insufficient evidence.",
             "citedReasonCode":"10.4","citedRulePassages":["[visa-10.4#a] A passage."],
             "evidenceRefs":["TX-1"]}
            """;

    @Test
    void a_prose_answer_triggers_the_repair_instead_of_an_exception() {
        LlmDecisionEngine engine = engineAnswering(PROSE, VALID_JSON);

        DisputeDecision decision = engine.decide(dispute(), bundle(), passages());

        assertThat(decision.decision()).isEqualTo(Decision.ACCEPT);
        assertThat(decision.agentVersion()).endsWith("+repaired");
    }

    @Test
    void two_prose_answers_yield_a_motivated_ESCALATE_and_never_an_exception() {
        LlmDecisionEngine engine = engineAnswering(PROSE, PROSE);

        DisputeDecision decision = engine.decide(dispute(), bundle(), passages());

        assertThat(decision.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(decision.agentVersion()).endsWith("+repair-failed");
        assertThat(decision.confidence()).isZero();
        assertThat(decision.rationale()).contains("usable JSON");
        assertThat(decision.citedRulePassages()).isNotEmpty();
    }

    // The REAL converter raises on prose, so the test cannot assume the wrong exception type
    private static LlmDecisionEngine engineAnswering(String first, String second) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        BeanOutputConverter<DecisionDraft> converter = new BeanOutputConverter<>(DecisionDraft.class);
        when(builder.build().prompt().system(anyString()).user(anyString()).call()
                .entity(DecisionDraft.class))
                .thenAnswer(call -> converter.convert(first))
                .thenAnswer(call -> converter.convert(second));

        return new LlmDecisionEngine(
                builder,
                new DraftValidator(Set.of("10.4")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "decision-llm@vTest",
                new ByteArrayResource("test prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private static Dispute dispute() {
        return new Dispute("D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12_000L, "EUR"), NOW, NOW.plusSeconds(2_592_000L),
                "I never ordered this");
    }

    private static EvidenceBundle bundle() {
        return new EvidenceBundle("D-1", "TX-1", "summary", List.of("one finding"),
                List.of("TX-1"), List.of("get_transaction"), false, "evidence@vTest", NOW);
    }

    private static List<String> passages() {
        return List.of("[visa-10.4#a] A passage.");
    }
}
