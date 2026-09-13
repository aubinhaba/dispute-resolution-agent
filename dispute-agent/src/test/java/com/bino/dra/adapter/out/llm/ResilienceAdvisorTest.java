package com.bino.dra.adapter.out.llm;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.bino.dra.application.port.out.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers the mechanics; the real SDK exceptions are exercised by ResilienceIT against real
// status codes, because AnthropicServiceException is abstract and built through a builder
class ResilienceAdvisorTest {

    @Test
    void a_transient_outage_is_retried_until_it_succeeds() {
        FakeChain chain = FakeChain.failing(2, new AnthropicIoException("connection reset"));
        ResilienceAdvisor advisor = advisor(3);

        ChatClientResponse response = advisor.adviseCall(request(), chain);

        assertThat(response).isNotNull();
        assertThat(chain.calls()).isEqualTo(3);
    }

    @Test
    void once_the_attempts_are_spent_the_outage_becomes_a_named_dependency() {
        FakeChain chain = FakeChain.failing(99, new AnthropicRetryableException("overloaded"));

        assertThatThrownBy(() -> advisor(3).adviseCall(request(), chain))
                .isInstanceOfSatisfying(DependencyUnavailableException.class, outage -> {
                    assertThat(outage.dependency()).isEqualTo("anthropic");
                    assertThat(outage.getCause()).isInstanceOf(AnthropicRetryableException.class);
                });
        assertThat(chain.calls()).isEqualTo(3);
    }

    // A bug of ours translated into an outage would produce a reassuring ESCALATE nobody fixes
    @Test
    void an_exception_that_is_not_the_providers_flows_through_untouched_and_unretried() {
        FakeChain chain = FakeChain.failing(99, new IllegalStateException("our own bug"));

        assertThatThrownBy(() -> advisor(3).adviseCall(request(), chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("our own bug");
        assertThat(chain.calls()).isEqualTo(1);
    }

    @Test
    void the_advisor_sits_below_the_budget_so_a_retried_attempt_costs_one_slot() {
        assertThat(advisor(3).getOrder())
                .isGreaterThan(new TokenBudgetAdvisor(1).getOrder())
                .isGreaterThan(ToolCallingAdvisor.DEFAULT_ORDER);
    }

    private static ResilienceAdvisor advisor(int attempts) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("anthropic-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .ignoreException(failure -> !ResilienceAdvisor.isProviderFailure(failure))
                .build());
        Retry retry = Retry.of("anthropic-test", RetryConfig.custom()
                .maxAttempts(attempts)
                .waitDuration(Duration.ofMillis(5))
                // The retry policy belongs to the advisor, not to this fixture
                .retryOnException(ResilienceAdvisor::isTransient)
                .build());
        return new ResilienceAdvisor(circuitBreaker, retry);
    }

    private static ChatClientRequest request() {
        return ChatClientRequest.builder().prompt(new Prompt("dispute")).build();
    }

    // Single-use, like the real chain: a second nextCall on the same instance answers
    // "No CallAdvisors available to execute", which is how a missing copy() shows up
    private static final class FakeChain implements CallAdvisorChain {

        private final Deque<RuntimeException> failures;
        private final int[] calls;
        private boolean used;

        private FakeChain(Deque<RuntimeException> failures, int[] calls) {
            this.failures = failures;
            this.calls = calls;
        }

        static FakeChain failing(int times, RuntimeException cause) {
            Deque<RuntimeException> failures = new ArrayDeque<>();
            for (int i = 0; i < times; i++) {
                failures.add(cause);
            }
            return new FakeChain(failures, new int[1]);
        }

        @Override
        public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
            if (used) {
                throw new IllegalStateException("No CallAdvisors available to execute");
            }
            used = true;
            calls[0]++;
            RuntimeException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            return ChatClientResponse.builder().chatResponse(new ChatResponse(List.of())).build();
        }

        @Override
        public List<CallAdvisor> getCallAdvisors() {
            return List.of();
        }

        @Override
        public CallAdvisorChain copy(CallAdvisor callAdvisor) {
            return new FakeChain(failures, calls);
        }

        int calls() {
            return calls[0];
        }
    }
}
