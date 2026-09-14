package com.bino.dra.adapter.out.llm;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Base modules wired by hand: resilience4j-spring-boot3 targets Boot 3 and has no Boot 4 equivalent
@Configuration
public class ResilienceConfig {

    static final String MCP = "mcp-payments";

    @Bean
    CircuitBreakerRegistry circuitBreakers(MeterRegistry meterRegistry) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    RetryRegistry retries(MeterRegistry meterRegistry) {
        RetryRegistry registry = RetryRegistry.ofDefaults();
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    CircuitBreaker modelCircuitBreaker(
            CircuitBreakerRegistry registry,
            @Value("${dra.resilience.model.sliding-window}") int window,
            @Value("${dra.resilience.model.failure-rate-threshold}") float threshold,
            @Value("${dra.resilience.model.open-duration}") Duration openDuration) {
        return registry.circuitBreaker(ResilienceAdvisor.DEPENDENCY,
                breaker(window, threshold, openDuration));
    }

    @Bean
    Retry modelRetry(
            RetryRegistry registry,
            @Value("${dra.resilience.model.max-attempts}") int attempts,
            @Value("${dra.resilience.model.wait}") Duration wait) {
        return registry.retry(ResilienceAdvisor.DEPENDENCY, retry(attempts, wait));
    }

    @Bean
    CircuitBreaker mcpCircuitBreaker(
            CircuitBreakerRegistry registry,
            @Value("${dra.resilience.mcp.sliding-window}") int window,
            @Value("${dra.resilience.mcp.failure-rate-threshold}") float threshold,
            @Value("${dra.resilience.mcp.open-duration}") Duration openDuration) {
        return registry.circuitBreaker(MCP, breaker(window, threshold, openDuration));
    }

    @Bean
    Retry mcpRetry(
            RetryRegistry registry,
            @Value("${dra.resilience.mcp.max-attempts}") int attempts,
            @Value("${dra.resilience.mcp.wait}") Duration wait) {
        return registry.retry(MCP, retry(attempts, wait));
    }

    // One customizer covers every ChatClient: Spring AI applies these to the prototype builder
    @Bean
    ChatClientCustomizer resilienceOnEveryChatClient(
            @Qualifier("modelCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("modelRetry") Retry retry) {
        ResilienceAdvisor advisor = new ResilienceAdvisor(circuitBreaker, retry);
        return builder -> builder.defaultAdvisors(advisor);
    }

    private static CircuitBreakerConfig breaker(int window, float threshold, Duration openDuration) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(window)
                .failureRateThreshold(threshold)
                .waitDurationInOpenState(openDuration)
                .ignoreException(failure -> !ResilienceAdvisor.isProviderFailure(failure))
                .build();
    }

    private static RetryConfig retry(int attempts, Duration wait) {
        return RetryConfig.custom()
                .maxAttempts(attempts)
                .waitDuration(wait)
                .retryOnException(ResilienceAdvisor::isTransient)
                .build();
    }
}
