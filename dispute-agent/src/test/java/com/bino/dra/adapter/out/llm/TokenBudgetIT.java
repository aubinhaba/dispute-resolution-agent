package com.bino.dra.adapter.out.llm;

import com.bino.dra.adapter.out.llm.TokenBudgetAdvisor.TokenBudgetExceededException;
import com.bino.dra.testsupport.FakeAnthropicServer;
import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// What the unit test cannot show: the advisor really is re-entered on every round of the
// framework's tool loop, and its exception leaves .call().entity() unwrapped
@NoDatabase
@SpringBootTest(properties = "spring.ai.anthropic.api-key=fake-key-never-sent-to-anthropic")
class TokenBudgetIT {

    private static final FakeAnthropicServer anthropic = FakeAnthropicServer.start();

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", anthropic::baseUrl);
    }

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @AfterAll
    static void stop() {
        anthropic.close();
    }

    @BeforeEach
    void reset() {
        anthropic.reset();
    }

    @Test
    void the_advisor_counts_the_tokens_of_BOTH_rounds_of_the_tool_loop() {
        anthropic.answerToolUse("lookup", 100, 20).answerText("done", 150, 30);
        TokenBudgetAdvisor budget = new TokenBudgetAdvisor(10_000);

        chatClientBuilder.build().prompt()
                .user("dispute").tools(new FakeTool()).advisors(budget).call().content();

        assertThat(budget.consumed()).isEqualTo(300);
    }

    @Test
    void past_the_budget_the_exception_arrives_unwrapped_and_the_second_call_never_happens() {
        anthropic.answerToolUse("lookup", 400, 100).answerText("done", 150, 30);
        TokenBudgetAdvisor budget = new TokenBudgetAdvisor(500);

        assertThatThrownBy(() -> chatClientBuilder.build().prompt()
                .user("dispute").tools(new FakeTool()).advisors(budget).call().content())
                .isInstanceOf(TokenBudgetExceededException.class);

        // One trip to the provider: the call that would have been billed is the one prevented
        assertThat(anthropic.requests()).hasSize(1);
    }

    static class FakeTool {
        @Tool(description = "Test-only tool whose sole purpose is to force a second round")
        String lookup() {
            return "ok";
        }
    }
}
