package com.bino.dra.adapter.out.agent;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Built through the production config, not a fixture: a fixture carrying the right predicates
// stayed green while the real beans carried the model's, and the MCP breaker never opened
class McpResilienceConfigTest {

    private final McpResilienceConfig config = new McpResilienceConfig();

    @Test
    void the_production_retry_retries_a_transport_outage() {
        Retry retry = config.mcpRetry(RetryRegistry.ofDefaults(), 3, Duration.ofMillis(5));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.decorateSupplier(retry, failing(transportFailure(), calls)).get())
                .isInstanceOf(ToolExecutionException.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void the_production_retry_leaves_a_business_error_to_the_model() {
        Retry retry = config.mcpRetry(RetryRegistry.ofDefaults(), 3, Duration.ofMillis(5));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.decorateSupplier(retry, failing(businessError(), calls)).get())
                .isInstanceOf(ToolExecutionException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void a_transport_outage_counts_against_the_production_breaker() {
        CircuitBreaker breaker = breaker();

        assertThatThrownBy(() -> breaker.executeSupplier(failing(transportFailure(), new AtomicInteger())))
                .isInstanceOf(ToolExecutionException.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void a_business_error_does_not_count_against_the_production_breaker() {
        CircuitBreaker breaker = breaker();

        assertThatThrownBy(() -> breaker.executeSupplier(failing(businessError(), new AtomicInteger())))
                .isInstanceOf(ToolExecutionException.class);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private CircuitBreaker breaker() {
        return config.mcpCircuitBreaker(CircuitBreakerRegistry.ofDefaults(), 10, 50f, Duration.ofSeconds(30));
    }

    private static Supplier<String> failing(RuntimeException failure, AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            throw failure;
        };
    }

    private static ToolExecutionException transportFailure() {
        return new ToolExecutionException(mock(ToolDefinition.class), new IOException("connection refused"));
    }

    private static ToolExecutionException businessError() {
        return new ToolExecutionException(mock(ToolDefinition.class),
                new IllegalStateException("Error calling tool: Unknown transactionId 'TX-MADE-UP'"));
    }
}
