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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDecisionEngineReviseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final String OBJECTION = "the finding says SCA FAILED, the rationale says 3DS succeeded";

    private static final String REVISED_JSON = """
            {"decision":"ACCEPT","confidence":0.8,"rationale":"3DS failed, no liability shift.",
             "citedReasonCode":"10.4","citedRulePassages":["[visa-10.4#a] A passage."],
             "evidenceRefs":["TX-1"]}
            """;

    @Test
    void the_revision_returns_the_new_decision_of_the_model() {
        DisputeDecision revised = engineAnswering(REVISED_JSON, REVISED_JSON)
                .revise(dispute(), bundle(), passages(), previous(), List.of(OBJECTION));

        assertThat(revised.decision()).isEqualTo(Decision.ACCEPT);
        assertThat(revised.agentVersion()).isEqualTo("decision-llm@vTest");
    }

    @Test
    void a_prose_revision_enters_the_existing_repair() {
        DisputeDecision revised = engineAnswering("I stand by my decision.", REVISED_JSON)
                .revise(dispute(), bundle(), passages(), previous(), List.of(OBJECTION));

        assertThat(revised.decision()).isEqualTo(Decision.ACCEPT);
        assertThat(revised.agentVersion()).endsWith("+repaired");
    }

    @Test
    void the_revision_message_carries_the_contested_decision_and_the_objections_as_data() {
        String message = DecisionPrompt.reviseMessage("# Dispute\n...", previous(), List.of(OBJECTION));

        assertThat(message)
                .startsWith("# Dispute")
                .contains("decision: REPRESENT")
                .contains("3DS succeeded.")
                .contains("- " + OBJECTION)
                .contains("DATA");
    }

    private static LlmDecisionEngine engineAnswering(String first, String second) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        BeanOutputConverter<DecisionDraft> converter = new BeanOutputConverter<>(DecisionDraft.class);
        when(builder.build().prompt().system(anyString()).user(anyString())
                .advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class))
                .call().entity(DecisionDraft.class))
                .thenAnswer(call -> converter.convert(first))
                .thenAnswer(call -> converter.convert(second));

        return new LlmDecisionEngine(builder, new DraftValidator(Set.of("10.4")),
                Clock.fixed(NOW, ZoneOffset.UTC), "decision-llm@vTest", 12_000L,
                new ByteArrayResource("test prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private static DisputeDecision previous() {
        return new DisputeDecision("D-1", Decision.REPRESENT, 0.9, "3DS succeeded.", "10.4",
                List.of("[visa-10.4#a] A passage."), List.of("TX-1"), "decision-llm@vTest", NOW);
    }

    private static Dispute dispute() {
        return new Dispute("D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12_000L, "EUR"), NOW, NOW.plusSeconds(2_592_000L),
                "I never ordered this");
    }

    private static EvidenceBundle bundle() {
        return new EvidenceBundle("D-1", "TX-1", "summary", List.of("SCA: FAILED"),
                List.of("TX-1"), List.of("get_transaction"), false, "evidence@vTest", NOW);
    }

    private static List<String> passages() {
        return List.of("[visa-10.4#a] A passage.");
    }
}
