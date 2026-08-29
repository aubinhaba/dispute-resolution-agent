package com.bino.dra.adapter.out.agent;

import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Not one assertion moved when the transport went from STDIO to Streamable HTTP (see ADR-0016)
@NoDatabase
@SpringBootTest(properties = {
        // The auto-configured ChatModel demands a key at startup; this test never calls it
        "spring.ai.anthropic.api-key=not-used-by-this-test"
})
class McpToolDiscoveryIT {

    private static final List<String> EXPECTED_TOOLS = List.of(
            "get_transaction", "get_customer_history", "get_related_transactions", "get_fulfillment_record");

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    void the_four_server_tools_are_discovered_by_the_client() {
        List<String> names = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
    }

    @Test
    void tool_descriptions_arrive_intact_on_the_client_side() {
        ToolCallback getTransaction = tool("get_transaction");

        assertThat(getTransaction.getToolDefinition().description())
                .contains("Start every dispute investigation with this tool")
                .contains("scaResult");
    }

    @Test
    void the_input_schema_exposes_real_parameter_names_and_not_arg0() {
        String schema = tool("get_transaction").getToolDefinition().inputSchema();

        assertThat(schema).contains("transactionId");
        assertThat(schema).doesNotContain("arg0");
    }

    @Test
    void optional_history_parameters_are_declared_optional() {
        String schema = tool("get_customer_history").getToolDefinition().inputSchema();

        assertThat(schema).contains("customerRef").contains("lookbackDays").contains("limit");
        assertThat(schema).contains("\"required\"").contains("customerRef");
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(cb -> cb.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool missing from tools/list: " + name));
    }
}
