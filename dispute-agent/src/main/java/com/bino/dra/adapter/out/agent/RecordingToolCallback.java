package com.bino.dra.adapter.out.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

final class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolCallRecorder recorder;

    RecordingToolCallback(ToolCallback delegate, ToolCallRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
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

        try {
            String result = delegate.call(toolInput, toolContext);
            recorder.recordSuccess(toolName, toolInput, result);
            return result;
        } catch (RuntimeException ex) {
            recorder.recordFailure(toolName);
            throw ex;
        }
    }
}
