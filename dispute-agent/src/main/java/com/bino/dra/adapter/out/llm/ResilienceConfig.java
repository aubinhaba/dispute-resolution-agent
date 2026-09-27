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
import java.util.function.Predicate;

// Base modules wired by hand: resilience4j-spring-boot3 targets Boot 3 and has no Boot 4 equivalent.
// The MCP breaker and retry live next to the tool callback whose failures they classify
@Configuration
public class ResilienceConfig {

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
                breaker(window, threshold, openDuration, ResilienceAdvisor::isProviderFailure));
    }

    @Bean
    Retry modelRetry(
            RetryRegistry registry,
            @Value("${dra.resilience.model.max-attempts}") int attempts,
            @Value("${dra.resilience.model.wait}") Duration wait) {
        return registry.retry(ResilienceAdvisor.DEPENDENCY, retry(attempts, wait, ResilienceAdvisor::isTransient));
    }

    // One customizer covers every ChatClient: Spring AI applies these to the prototype builder
    @Bean
    ChatClientCustomizer resilienceOnEveryChatClient(
            @Qualifier("modelCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("modelRetry") Retry retry) {
        ResilienceAdvisor advisor = new ResilienceAdvisor(circuitBreaker, retry);
        return builder -> builder.defaultAdvisors(advisor);
    }

    // The predicate is per dependency: each client wraps its failures in its own exception types
    public static CircuitBreakerConfig breaker(int window, float threshold, Duration openDuration,
                                               Predicate<Throwable> countsAsFailure) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(window)
                .failureRateThreshold(threshold)
                .waitDurationInOpenState(openDuration)
                .ignoreException(failure -> !countsAsFailure.test(failure))
                .build();
    }

    public static RetryConfig retry(int attempts, Duration wait, Predicate<Throwable> retryable) {
        return RetryConfig.custom()
                .maxAttempts(attempts)
                .waitDuration(wait)
                .retryOnException(retryable)
                .build();
    }
}
