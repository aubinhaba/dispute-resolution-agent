package com.bino.dra.adapter.out.agent;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmEvidenceAgentProseTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private static final String PROSE = """
            Based on my investigation, the transaction appears legitimate and 3DS succeeded.
            """;

    @Test
    void a_prose_answer_does_not_let_an_exception_cross_the_port() {
        assertThatCode(() -> agentAnswering(PROSE).gather(dispute())).doesNotThrowAnyException();
    }

    @Test
    void the_degraded_bundle_loses_the_narrative_but_keeps_what_is_attested() {
        EvidenceBundle bundle = agentAnswering(PROSE).gather(dispute());

        assertThat(bundle.summary()).isEmpty();
        assertThat(bundle.findings()).isEmpty();

        assertThat(bundle.disputeId()).isEqualTo("D-1");
        assertThat(bundle.transactionId()).isEqualTo("TX-1");
        assertThat(bundle.gatheredAt()).isEqualTo(NOW);
    }

    @Test
    void the_agent_version_records_the_incident_in_the_audit_trail() {
        assertThat(agentAnswering(PROSE).gather(dispute()).agentVersion()).endsWith("+unparsed");
    }

    private static LlmEvidenceAgent agentAnswering(String answer) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        BeanOutputConverter<EvidenceDraft> converter = new BeanOutputConverter<>(EvidenceDraft.class);
        when(builder.build().prompt().system(anyString()).user(anyString()).tools(any(Object[].class))
                .call().entity(EvidenceDraft.class))
                .thenAnswer(call -> converter.convert(answer));

        ToolCallbackProvider noTools = () -> new ToolCallback[0];

        return new LlmEvidenceAgent(
                builder,
                noTools,
                Clock.fixed(NOW, ZoneOffset.UTC),
                8,
                "evidence-llm@vTest",
                new ByteArrayResource("test prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private static Dispute dispute() {
        return new Dispute("D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12_000L, "EUR"), NOW, NOW.plusSeconds(2_592_000L),
                "I never ordered this");
    }
}
