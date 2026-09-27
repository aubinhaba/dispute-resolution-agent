package com.bino.dra.adapter.out.agent;

import com.bino.dra.adapter.out.llm.ResilienceConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// MCP failures are ToolExecutionExceptions, never AnthropicExceptions: the model's predicates
// would retry nothing here and leave the breaker ignoring every outage (ADR-0023)
@Configuration
public class McpResilienceConfig {

    @Bean
    CircuitBreaker mcpCircuitBreaker(
            CircuitBreakerRegistry registry,
            @Value("${dra.resilience.mcp.sliding-window}") int window,
            @Value("${dra.resilience.mcp.failure-rate-threshold}") float threshold,
            @Value("${dra.resilience.mcp.open-duration}") Duration openDuration) {
        return registry.circuitBreaker(RecordingToolCallback.DEPENDENCY,
                ResilienceConfig.breaker(window, threshold, openDuration, RecordingToolCallback::isTransportFailure));
    }

    @Bean
    Retry mcpRetry(
            RetryRegistry registry,
            @Value("${dra.resilience.mcp.max-attempts}") int attempts,
            @Value("${dra.resilience.mcp.wait}") Duration wait) {
        return registry.retry(RecordingToolCallback.DEPENDENCY,
                ResilienceConfig.retry(attempts, wait, RecordingToolCallback::isTransportFailure));
    }
}
