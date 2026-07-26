package com.bino.dra.adapter.out.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCallRecorderTest {

    @Test
    void allows_exactly_the_budgeted_number_of_calls() {
        ToolCallRecorder recorder = new ToolCallRecorder(3);

        assertThat(recorder.tryConsume()).isTrue();
        assertThat(recorder.tryConsume()).isTrue();
        assertThat(recorder.tryConsume()).isTrue();

        assertThat(recorder.tryConsume()).isFalse();
        assertThat(recorder.budgetExhausted()).isTrue();
        assertThat(recorder.consumed()).isEqualTo(3);
    }

    @Test
    void does_not_flag_exhaustion_while_under_the_cap() {
        ToolCallRecorder recorder = new ToolCallRecorder(2);
        recorder.tryConsume();

        assertThat(recorder.budgetExhausted()).isFalse();
    }

    @Test
    void rejects_a_nonsensical_budget_at_construction() {
        assertThatThrownBy(() -> new ToolCallRecorder(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxToolCalls");
    }

    @Test
    void collects_identifiers_from_arguments_and_result() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        recorder.recordSuccess("get_transaction",
                "{\"transactionId\":\"TXN-EVAL-002\"}",
                "{\"transactionId\":\"TXN-EVAL-002\",\"customerRef\":\"CUST-M4XA1\",\"cardLast4\":\"1881\"}");

        assertThat(recorder.evidenceRefs()).containsExactly("TXN-EVAL-002", "CUST-M4XA1");
        assertThat(recorder.toolsUsed()).containsExactly("get_transaction");
    }

    @Test
    void unwraps_json_nested_inside_the_mcp_content_envelope() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        // Real shape of an MCP response: content blocks whose payload is escaped JSON
        String mcpResponse = """
                [{"type":"text","text":"[{\\"transactionId\\":\\"TXN-H-M4XA1-1\\"},{\\"transactionId\\":\\"TXN-H-M4XA1-2\\"}]"}]""";

        recorder.recordSuccess("get_customer_history", "{\"customerRef\":\"CUST-M4XA1\"}", mcpResponse);

        assertThat(recorder.evidenceRefs())
                .containsExactly("CUST-M4XA1", "TXN-H-M4XA1-1", "TXN-H-M4XA1-2");
    }

    @Test
    void deduplicates_identifiers_seen_more_than_once() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        recorder.recordSuccess("get_transaction", "{\"transactionId\":\"TXN-EVAL-003\"}",
                "{\"transactionId\":\"TXN-EVAL-003\"}");
        recorder.recordSuccess("get_fulfillment_record", "{\"transactionId\":\"TXN-EVAL-003\"}",
                "{\"found\":true,\"record\":{\"transactionId\":\"TXN-EVAL-003\"}}");

        assertThat(recorder.evidenceRefs()).containsExactly("TXN-EVAL-003");
        assertThat(recorder.toolsUsed()).containsExactly("get_transaction", "get_fulfillment_record");
    }

    @Test
    void a_failed_call_yields_no_evidence() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        recorder.recordFailure("get_transaction");

        assertThat(recorder.evidenceRefs()).isEmpty();
        assertThat(recorder.toolsUsed()).containsExactly("get_transaction");
    }

    @Test
    void ignores_tool_output_that_is_not_json() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        recorder.recordSuccess("get_transaction", "{\"transactionId\":\"TXN-EVAL-001\"}", "free text error");

        assertThat(recorder.evidenceRefs()).containsExactly("TXN-EVAL-001");
    }

    @Test
    void extracts_only_the_contract_identifier_fields() {
        ToolCallRecorder recorder = new ToolCallRecorder(5);

        recorder.recordSuccess("get_transaction", "{}",
                "{\"transactionId\":\"TXN-EVAL-001\",\"merchantId\":\"MERCH-ELEC-01\",\"psp\":\"STRIPE\"}");

        assertThat(recorder.evidenceRefs()).containsExactly("TXN-EVAL-001");
    }
}
