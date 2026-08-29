package com.bino.dra.adapter.out.agent;

import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@NoDatabase
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RawToolLoopDemoIT {

    private static final int MAX_TURNS = 6;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolCallbackProvider mcpToolCallbacks;

    @Value("${spring.ai.anthropic.chat.options.model}")
    private String model;

    @Test
    void runs_the_tool_calling_loop_turn_by_turn() {
        List<ToolCallback> tools = List.of(mcpToolCallbacks.getToolCallbacks());
        Map<String, ToolCallback> byName = tools.stream()
                .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), Function.identity()));

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .temperature(0.0)
                .maxTokens(2048)
                .toolCallbacks(tools)
                .build();

        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage(
                "You are a payment dispute investigator. Use the tools to investigate the dispute, "
                        + "then return a 3-sentence summary. Start by retrieving the transaction."));
        history.add(new UserMessage(
                "Dispute EVAL-002 on transaction TXN-EVAL-002 (Visa reason code 10.4, alleged fraud). "
                        + "What does the data say?"));

        int turnsTaken = 0;
        int toolCalls = 0;
        String finalAnswer = null;

        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            turnsTaken = turn;

            ChatResponse response = chatModel.call(new Prompt(history, options));
            AssistantMessage assistant = response.getResult().getOutput();

            history.add(assistant);

            if (!assistant.hasToolCalls()) {
                finalAnswer = assistant.getText();
                System.out.printf("Turn %d — final answer:%n%s%n", turn, finalAnswer);
                break;
            }

            List<ToolResponseMessage.ToolResponse> results = new ArrayList<>();
            for (AssistantMessage.ToolCall request : assistant.getToolCalls()) {
                System.out.printf("Turn %d — model requests: %s(%s)%n", turn, request.name(), request.arguments());

                ToolCallback tool = byName.get(request.name());
                String result = (tool == null)
                        ? "Unknown tool: " + request.name()
                        : tool.call(request.arguments());
                toolCalls++;

                // Correlated by id, not by position: a turn may request several tools at once
                results.add(new ToolResponseMessage.ToolResponse(request.id(), request.name(), result));
                System.out.printf("        -> %s%n", truncate(result));
            }

            history.add(ToolResponseMessage.builder().responses(results).build());
        }

        assertThat(turnsTaken).isGreaterThan(1);
        assertThat(toolCalls).isPositive();
        assertThat(finalAnswer).isNotBlank();
    }

    private static String truncate(String text) {
        return text.length() <= 220 ? text : text.substring(0, 220) + "... (" + text.length() + " chars)";
    }
}
