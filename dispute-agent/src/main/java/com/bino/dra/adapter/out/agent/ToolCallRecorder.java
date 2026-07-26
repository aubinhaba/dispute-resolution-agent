package com.bino.dra.adapter.out.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// One instance per investigation, never a shared bean: a singleton would merge concurrent trails
final class ToolCallRecorder {

    static final String BUDGET_EXHAUSTED_MESSAGE =
            "Tool call budget exhausted for this investigation. No further tool calls are allowed. "
                    + "Summarize the evidence you have already gathered and finish now.";

    private static final Set<String> IDENTIFIER_FIELDS = Set.of("transactionId", "customerRef");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final int maxToolCalls;
    private final List<String> toolsUsed = new ArrayList<>();
    private final Set<String> evidenceRefs = new LinkedHashSet<>();
    private int consumed;
    private boolean budgetExhausted;

    ToolCallRecorder(int maxToolCalls) {
        if (maxToolCalls < 1) {
            throw new IllegalArgumentException("maxToolCalls must be >= 1, got " + maxToolCalls);
        }
        this.maxToolCalls = maxToolCalls;
    }

    // Spring AI's tool-calling loop has no turn limit, so the cap lives here (see ADR-0002)
    boolean tryConsume() {
        if (consumed >= maxToolCalls) {
            budgetExhausted = true;
            return false;
        }
        consumed++;
        return true;
    }

    void recordSuccess(String toolName, String argumentsJson, String resultJson) {
        toolsUsed.add(toolName);
        collectIdentifiers(argumentsJson);
        collectIdentifiers(resultJson);
    }

    void recordFailure(String toolName) {
        toolsUsed.add(toolName);
    }

    List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    List<String> evidenceRefs() {
        return List.copyOf(evidenceRefs);
    }

    boolean budgetExhausted() {
        return budgetExhausted;
    }

    int consumed() {
        return consumed;
    }

    String budgetExhaustedMessage() {
        return BUDGET_EXHAUSTED_MESSAGE;
    }

    private void collectIdentifiers(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            walk(JSON.readTree(json));
        } catch (Exception ignored) {
            // Non-JSON tool output: a poorer trail, never a failed investigation
        }
    }

    private void walk(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (IDENTIFIER_FIELDS.contains(entry.getKey()) && value.isTextual()) {
                    String id = value.asText().trim();
                    if (!id.isEmpty()) {
                        evidenceRefs.add(id);
                    }
                }
                walk(value);
            });
        } else if (node.isArray()) {
            node.forEach(this::walk);
        } else if (node.isTextual()) {
            // An MCP payload is escaped JSON inside content blocks: the envelope alone holds no id
            unwrapEmbeddedJson(node.asText());
        }
    }

    private void unwrapEmbeddedJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            collectIdentifiers(trimmed);
        }
    }
}
