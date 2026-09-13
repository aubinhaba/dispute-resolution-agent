package com.bino.dra.adapter.out.llm;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.bino.dra.application.port.out.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import java.util.function.Supplier;

// Circuit breaker shared across disputes: a provider outage is not per-request state
public final class ResilienceAdvisor implements CallAdvisor {

    public static final String DEPENDENCY = "anthropic";

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int FIRST_SERVER_ERROR = 500;

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilienceAdvisor(CircuitBreaker circuitBreaker, Retry retry) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    // A 400 (exhausted quota) or a 401 never heals on a second attempt: retrying only adds latency
    public static boolean isTransient(Throwable failure) {
        if (failure instanceof AnthropicServiceException service) {
            int status = service.statusCode();
            return status == TOO_MANY_REQUESTS || status >= FIRST_SERVER_ERROR;
        }
        return failure instanceof AnthropicException;
    }

    // Only provider failures open the breaker: a bug of ours says nothing about the provider
    public static boolean isProviderFailure(Throwable failure) {
        return failure instanceof AnthropicException;
    }

    @Override
    public String getName() {
        return "dra-resilience";
    }

    // Below the token budget, so a retried attempt costs one budget slot rather than three
    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 20;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Supplier<ChatClientResponse> call = () -> chain.copy(this).nextCall(request);
        Supplier<ChatClientResponse> guarded =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));

        try {
            return guarded.get();
        } catch (CallNotPermittedException breakerOpen) {
            throw new DependencyUnavailableException(DEPENDENCY,
                    "circuit breaker open, the call never left", breakerOpen);
        } catch (AnthropicException outage) {
            // Anything that is not a provider exception keeps flowing: a bug must stay a FAILED
            throw new DependencyUnavailableException(DEPENDENCY, detail(outage), outage);
        }
    }

    private static String detail(AnthropicException outage) {
        return outage instanceof AnthropicServiceException service
                ? "provider answered " + service.statusCode()
                : outage.getClass().getSimpleName();
    }
}
