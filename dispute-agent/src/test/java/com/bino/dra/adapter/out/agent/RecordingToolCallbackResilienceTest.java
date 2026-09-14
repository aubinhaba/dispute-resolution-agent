package com.bino.dra.adapter.out.agent;

import com.bino.dra.application.port.out.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// SyncMcpToolCallback wraps both an outage and an isError result in a ToolExecutionException.
// Getting the split wrong would cost the self-correction the tool loop was built for
class RecordingToolCallbackResilienceTest {

    @Test
    void a_business_error_stays_a_message_for_the_model_and_is_not_retried() {
        ToolCallback delegate = failingTool(
                new IllegalStateException("Error calling tool: Unknown transactionId 'TX-MADE-UP'"));

        assertThatThrownBy(() -> decorator(delegate).call("{}", null))
                .isInstanceOf(ToolExecutionException.class);

        verify(delegate, times(1)).call(anyString(), any());
    }

    @Test
    void a_transport_outage_is_retried_then_becomes_a_named_dependency() {
        ToolCallback delegate = failingTool(new IOException("connection refused"));

        assertThatThrownBy(() -> decorator(delegate).call("{}", null))
                .isInstanceOfSatisfying(DependencyUnavailableException.class,
                        outage -> assertThat(outage.dependency()).isEqualTo("mcp-payments"));

        verify(delegate, times(3)).call(anyString(), any());
    }

    private static RecordingToolCallback decorator(ToolCallback delegate) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("mcp-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .ignoreException(failure -> !RecordingToolCallback.isTransportFailure(failure))
                .build());
        Retry retry = Retry.of("mcp-test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(5))
                .retryOnException(RecordingToolCallback::isTransportFailure)
                .build());
        return new RecordingToolCallback(delegate, new ToolCallRecorder(8), circuitBreaker, retry);
    }

    private static ToolCallback failingTool(Throwable cause) {
        ToolCallback delegate = mock(ToolCallback.class, RETURNS_DEEP_STUBS);
        when(delegate.getToolDefinition().name()).thenReturn("get_transaction");
        // Resolved before the next when(): stubbing a mock inside an unfinished stubbing throws
        ToolDefinition definition = delegate.getToolDefinition();
        when(delegate.call(anyString(), any()))
                .thenThrow(new ToolExecutionException(definition, cause));
        return delegate;
    }
}
