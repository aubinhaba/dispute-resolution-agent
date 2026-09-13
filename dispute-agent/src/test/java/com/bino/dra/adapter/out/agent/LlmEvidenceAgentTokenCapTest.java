package com.bino.dra.adapter.out.agent;

import com.bino.dra.adapter.out.llm.TokenBudgetAdvisor.TokenBudgetExceededException;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
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

// Same doctrine as +unparsed: the tools already answered and the recorder attests them, so only
// the narrative is lost. The suffix keeps the incident countable in the audit trail
class LlmEvidenceAgentTokenCapTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Test
    void a_spent_budget_does_not_let_an_exception_cross_the_port() {
        assertThatCode(() -> agentExceedingItsBudget().gather(dispute())).doesNotThrowAnyException();
    }

    @Test
    void the_agent_version_records_the_cap_in_the_audit_trail() {
        EvidenceBundle bundle = agentExceedingItsBudget().gather(dispute());

        assertThat(bundle.agentVersion()).endsWith("+token-capped");
        assertThat(bundle.summary()).isEmpty();
        assertThat(bundle.disputeId()).isEqualTo("D-1");
        assertThat(bundle.gatheredAt()).isEqualTo(NOW);
    }

    private static LlmEvidenceAgent agentExceedingItsBudget() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().system(anyString()).user(anyString()).tools(any(Object[].class))
                .advisors(any(Advisor[].class)).call().entity(EvidenceDraft.class))
                .thenThrow(new TokenBudgetExceededException(40_100L, 40_000L));

        ToolCallbackProvider noTools = () -> new ToolCallback[0];

        return new LlmEvidenceAgent(
                builder,
                noTools,
                CircuitBreaker.ofDefaults("mcp-test"),
                Retry.ofDefaults("mcp-test"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                8,
                40_000L,
                "evidence-llm@vTest",
                new ByteArrayResource("test prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private static Dispute dispute() {
        return new Dispute("D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12_000L, "EUR"), NOW, NOW.plusSeconds(2_592_000L), "I never ordered this");
    }
}
