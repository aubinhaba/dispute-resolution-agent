package com.bino.dra.adapter.out.llm;

import com.bino.dra.application.port.out.DependencyUnavailableException;
import com.bino.dra.testsupport.FakeAnthropicServer;
import com.bino.dra.testsupport.NoDatabase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The fake provider returns real status codes, so the SDK builds its own real exceptions. The
// advisor is the one the ChatClientCustomizer wires in, so the real wiring is under test too
@NoDatabase
@SpringBootTest(properties = {
        "spring.ai.anthropic.api-key=fake-key-never-sent-to-anthropic",
        "dra.resilience.model.max-attempts=3",
        "dra.resilience.model.wait=10ms",
        // Window of 6 = two calls of three attempts: the breaker opens on the second, not the first
        "dra.resilience.model.sliding-window=6",
        "dra.resilience.model.failure-rate-threshold=50",
        "dra.resilience.model.open-duration=30s"})
class ResilienceIT {

    private static final FakeAnthropicServer anthropic = FakeAnthropicServer.start();

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", anthropic::baseUrl);
    }

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    @Qualifier("modelCircuitBreaker")
    private CircuitBreaker circuitBreaker;

    @AfterAll
    static void stop() {
        anthropic.close();
    }

    @BeforeEach
    void reset() {
        anthropic.reset();
        // The breaker is a shared singleton, which is its point: without a reset the test order
        // would become a hidden parameter
        circuitBreaker.reset();
    }

    @Test
    void a_transient_outage_is_retried_and_the_dispute_still_completes() {
        anthropic.answerStatus(500).answerText("done", 10, 5);

        String answer = call();

        assertThat(answer).isNotNull();
        // Two requests: the failure then the success. Not six - the SDK retry is off
        assertThat(anthropic.requests()).hasSize(2);
    }

    @Test
    void a_lasting_outage_becomes_an_unavailable_dependency_after_N_attempts() {
        anthropic.answerStatus(500).answerStatus(500).answerStatus(500).answerStatus(500);

        assertThatThrownBy(this::call)
                .isInstanceOfSatisfying(DependencyUnavailableException.class,
                        outage -> assertThat(outage.dependency()).isEqualTo("anthropic"));

        // EXACTLY 3, the attempt ceiling. Nine would mean two owners of the retry
        assertThat(anthropic.requests()).hasSize(3);
    }

    // A 400 - the exhausted-quota case - does not heal on a retry; retrying only hides the cause
    @Test
    void a_permanent_error_is_not_retried() {
        anthropic.answerStatus(400).answerStatus(400).answerStatus(400);

        assertThatThrownBy(this::call).isInstanceOf(DependencyUnavailableException.class);

        assertThat(anthropic.requests()).hasSize(1);
    }

    @Test
    void with_the_breaker_open_the_next_call_never_reaches_the_provider() {
        for (int i = 0; i < 8; i++) {
            anthropic.answerStatus(500);
        }
        assertThatThrownBy(this::call).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(this::call).isInstanceOf(DependencyUnavailableException.class);
        int requestsBefore = anthropic.requests().size();

        assertThatThrownBy(this::call).isInstanceOf(DependencyUnavailableException.class);

        assertThat(anthropic.requests()).hasSize(requestsBefore);
    }

    private String call() {
        return chatClientBuilder.build().prompt().user("dispute").call().content();
    }
}
