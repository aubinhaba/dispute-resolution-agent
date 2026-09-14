package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.llm.TokenBudgetAdvisor.TokenBudgetExceededException;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
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

// The only realistic path: the first call starts on a fresh budget, so it is the repair that the
// cap refuses - and a repair is what doubles the price of a case (see ADR-0014)
class LlmDecisionEngineTokenCapTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    // Citation without a chunk id: the validator refuses it, which is what starts the repair
    private static final String UNATTESTABLE_JSON = """
            {"decision":"REPRESENT","confidence":0.8,"rationale":"3DS succeeded.",
             "citedReasonCode":"10.4","citedRulePassages":["A passage with no identifier."],
             "evidenceRefs":["TX-1"]}
            """;

    @Test
    void a_budget_spent_before_the_repair_yields_a_motivated_ESCALATE() {
        DisputeDecision decision = engineWhoseRepairExceeds().decide(dispute(), bundle(), passages());

        assertThat(decision.decision()).isEqualTo(Decision.ESCALATE);
        assertThat(decision.agentVersion()).endsWith("+token-capped");
        assertThat(decision.confidence()).isZero();
        assertThat(decision.rationale()).contains("token budget");
        assertThat(decision.citedRulePassages()).isNotEmpty();
    }

    private static LlmDecisionEngine engineWhoseRepairExceeds() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        BeanOutputConverter<DecisionDraft> converter = new BeanOutputConverter<>(DecisionDraft.class);
        when(builder.build().prompt().system(anyString()).user(anyString())
                .advisors(any(Advisor[].class)).call().entity(DecisionDraft.class))
                .thenAnswer(call -> converter.convert(UNATTESTABLE_JSON))
                .thenThrow(new TokenBudgetExceededException(12_400L, 12_000L));

        return new LlmDecisionEngine(
                builder,
                new DraftValidator(Set.of("10.4")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "decision-llm@vTest",
                12_000L,
                new ByteArrayResource("test prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private static Dispute dispute() {
        return new Dispute("D-1", "TX-1", "M-1", Network.VISA, "10.4",
                new Money(12_000L, "EUR"), NOW, NOW.plusSeconds(2_592_000L), "I never ordered this");
    }

    private static EvidenceBundle bundle() {
        return new EvidenceBundle("D-1", "TX-1", "summary", List.of("one finding"),
                List.of("TX-1"), List.of("get_transaction"), false, "evidence@vTest", NOW);
    }

    private static List<String> passages() {
        return List.of("[visa-10.4#a] A passage.");
    }
}
