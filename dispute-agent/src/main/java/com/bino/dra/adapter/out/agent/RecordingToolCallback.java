package com.bino.dra.adapter.out.agent;

import com.bino.dra.application.port.out.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Supplier;

final class RecordingToolCallback implements ToolCallback {

    static final String DEPENDENCY = "mcp-payments";

    private final ToolCallback delegate;
    private final ToolCallRecorder recorder;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    RecordingToolCallback(ToolCallback delegate, ToolCallRecorder recorder,
                          CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    // SyncMcpToolCallback wraps an isError result in an IllegalStateException: the only typed
    // discriminator between a business answer and a transport outage
    static boolean isBusinessError(Throwable failure) {
        return failure instanceof ToolExecutionException
                && failure.getCause() instanceof IllegalStateException;
    }

    static boolean isTransportFailure(Throwable failure) {
        return failure instanceof ToolExecutionException && !isBusinessError(failure);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();

        if (!recorder.tryConsume()) {
            return recorder.budgetExhaustedMessage();
        }

        // The tool budget is spent once whatever the network does: it counts model decisions
        Supplier<String> guarded = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.call(toolInput, toolContext)));

        try {
            String result = guarded.get();
            recorder.recordSuccess(toolName, toolInput, result);
            return result;
        } catch (CallNotPermittedException breakerOpen) {
            recorder.recordFailure(toolName);
            throw new DependencyUnavailableException(DEPENDENCY,
                    "circuit breaker open, the call never left", breakerOpen);
        } catch (RuntimeException failure) {
            recorder.recordFailure(toolName);
            if (isTransportFailure(failure)) {
                throw new DependencyUnavailableException(DEPENDENCY, "tool server unreachable", failure);
            }
            // Rethrown as is: Spring AI turns it into a tool message the model corrects itself from
            throw failure;
        }
    }
}
