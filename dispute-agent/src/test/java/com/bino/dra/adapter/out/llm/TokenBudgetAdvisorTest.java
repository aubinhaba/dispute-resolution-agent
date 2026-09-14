package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.llm.TokenBudgetAdvisor.TokenBudgetExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBudgetAdvisorTest {

    @Test
    void the_tokens_of_every_round_add_up() {
        FakeChain chain = new FakeChain(response(100, 20), response(150, 30));
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(10_000);

        advisor.adviseCall(request(), chain);
        advisor.adviseCall(request(), chain);

        // 120 + 180, which is exactly what the final response alone would not say
        assertThat(advisor.consumed()).isEqualTo(300);
    }

    @Test
    void a_response_without_usage_does_not_break_the_accounting() {
        FakeChain chain = new FakeChain(new ChatResponse(List.of()), null);
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(10_000);

        advisor.adviseCall(request(), chain);
        advisor.adviseCall(request(), chain);

        assertThat(advisor.consumed()).isZero();
    }

    @Test
    void past_the_budget_the_next_round_is_refused_before_any_call() {
        FakeChain chain = new FakeChain(response(400, 100), response(1, 1));
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(500);

        advisor.adviseCall(request(), chain);

        assertThatThrownBy(() -> advisor.adviseCall(request(), chain))
                .isInstanceOf(TokenBudgetExceededException.class)
                .hasMessageContaining("500");
        assertThat(chain.calls()).isEqualTo(1);
    }

    @Test
    void the_exception_carries_what_an_escalation_needs_to_say() {
        FakeChain chain = new FakeChain(response(600, 0));
        TokenBudgetAdvisor advisor = new TokenBudgetAdvisor(500);
        advisor.adviseCall(request(), chain);

        assertThatThrownBy(() -> advisor.adviseCall(request(), chain))
                .isInstanceOfSatisfying(TokenBudgetExceededException.class, capped -> {
                    assertThat(capped.consumed()).isEqualTo(600);
                    assertThat(capped.maxTokens()).isEqualTo(500);
                });
    }

    @Test
    void the_advisor_runs_after_the_tool_loop_or_it_would_see_a_single_round() {
        assertThat(new TokenBudgetAdvisor(1).getOrder())
                .isGreaterThan(ToolCallingAdvisor.DEFAULT_ORDER);
    }

    private static ChatClientRequest request() {
        return ChatClientRequest.builder().prompt(new Prompt("dispute")).build();
    }

    private static ChatResponse response(int input, int output) {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(input, output))
                .build());
    }

    private static final class FakeChain implements CallAdvisorChain {

        private final Deque<ChatResponse> responses = new ArrayDeque<>();
        private int calls;

        FakeChain(ChatResponse... responses) {
            for (ChatResponse response : responses) {
                this.responses.add(response == null ? new ChatResponse(List.of()) : response);
            }
        }

        @Override
        public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
            calls++;
            return ChatClientResponse.builder().chatResponse(responses.poll()).build();
        }

        @Override
        public List<CallAdvisor> getCallAdvisors() {
            return List.of();
        }

        // The same instance: ToolCallingAdvisor copies the chain on every round, and a copy
        // starting from zero would reset the budget each time
        @Override
        public CallAdvisorChain copy(CallAdvisor callAdvisor) {
            return this;
        }

        int calls() {
            return calls;
        }
    }
}
