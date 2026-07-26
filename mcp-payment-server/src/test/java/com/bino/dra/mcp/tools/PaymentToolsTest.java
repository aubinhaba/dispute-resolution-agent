package com.bino.dra.mcp.tools;

import com.bino.dra.mcp.data.MockPaymentDataStore;
import com.bino.dra.mcp.model.FulfillmentLookupResult;
import com.bino.dra.mcp.model.TransactionSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentToolsTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private PaymentTools tools;

    @BeforeEach
    void setUp() {
        tools = new PaymentTools(new MockPaymentDataStore(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Nested
    @DisplayName("Input validation — the model can supply anything")
    class InputValidation {

        @Test
        @DisplayName("hallucinated transaction id -> error with a corrective message (not an empty 200)")
        void hallucinatedTransactionId_isRejectedWithGuidance() {
            assertThatThrownBy(() -> tools.getTransaction("TXN-LOOKS-PLAUSIBLE-42"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TXN-LOOKS-PLAUSIBLE-42")
                    .hasMessageContaining("do not guess");
        }

        @Test
        @DisplayName("unknown customerRef -> explicit error (≠ customer with no history)")
        void unknownCustomerRef_isRejected() {
            assertThatThrownBy(() -> tools.getCustomerHistory("CUST-INVENTED", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CUST-INVENTED");
        }

        @Test
        @DisplayName("blank required parameter -> immediate rejection")
        void blankRequiredParameter_isRejected() {
            assertThatThrownBy(() -> tools.getTransaction("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transactionId");
        }

        @Test
        @DisplayName("get_related_transactions distinguishes unknown id (error) from 'nothing related' (empty)")
        void relatedTransactions_unknownVsEmpty() {
            assertThatThrownBy(() -> tools.getRelatedTransactions("TXN-NOPE"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(tools.getRelatedTransactions("TXN-EVAL-001")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Context budget — history bounds")
    class ContextBudget {

        @Test
        @DisplayName("missing optional params -> defaults (90 days / 20 entries)")
        void missingOptionalParams_useDefaults() {
            List<TransactionSummaryDto> history = tools.getCustomerHistory("CUST-M4XA1", null, null);

            assertThat(history).hasSize(5);
        }

        @Test
        @DisplayName("oversized limit requested by the model -> clamped, no context explosion")
        void oversizedLimit_isClamped() {
            List<TransactionSummaryDto> history = tools.getCustomerHistory("CUST-M4XA1", 365, 5000);

            assertThat(history).hasSizeLessThanOrEqualTo(50);
        }

        @Test
        @DisplayName("negative or zero values -> fall back to defaults (robust, no 500)")
        void nonPositiveValues_fallBackToDefaults() {
            List<TransactionSummaryDto> history = tools.getCustomerHistory("CUST-M4XA1", -3, 0);

            assertThat(history).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Responses designed for a model reader")
    class ModelFacingOutputs {

        @Test
        @DisplayName("no fulfillment (digital goods) -> found=false + explanatory note, not an error")
        void digitalGoods_returnAnExplainedAbsence() {
            FulfillmentLookupResult result = tools.getFulfillmentRecord("TXN-EVAL-001");

            assertThat(result.found()).isFalse();
            assertThat(result.record()).isNull();
            assertThat(result.note()).contains("Nothing was shipped");
        }

        @Test
        @DisplayName("delivery proof (EVAL-003) -> found=true + full record")
        void deliveredGoods_returnTheRecord() {
            FulfillmentLookupResult result = tools.getFulfillmentRecord("TXN-EVAL-003");

            assertThat(result.found()).isTrue();
            assertThat(result.record().deliveryStatus()).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("amounts carry a pre-computed display string (anti LLM-arithmetic error)")
        void amounts_carryPreformattedDisplay() {
            var txn = tools.getTransaction("TXN-EVAL-004");

            assertThat(txn.amount().minorUnits()).isEqualTo(150_000L);
            assertThat(txn.amount().formatted()).isEqualTo("1500.00 EUR");
        }
    }

    @Nested
    @DisplayName("PCI invariant: never a PAN in a tool output")
    class PciInvariant {

        @Test
        @DisplayName("no PAN-like digit sequence in the JSON of the 4 tools")
        void noPanLikeDigitSequence_inAnyToolOutput() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            Pattern panLike = Pattern.compile("\\d{9,}");

            for (String txnId : List.of("TXN-EVAL-001", "TXN-EVAL-002", "TXN-EVAL-003", "TXN-EVAL-004")) {
                var txn = tools.getTransaction(txnId);
                String allOutputs = mapper.writeValueAsString(txn)
                        + mapper.writeValueAsString(tools.getCustomerHistory(txn.customerRef(), null, null))
                        + mapper.writeValueAsString(tools.getRelatedTransactions(txnId))
                        + mapper.writeValueAsString(tools.getFulfillmentRecord(txnId));

                assertThat(panLike.matcher(allOutputs).find())
                        .as("Tool output for %s: PAN-like digit sequence is forbidden", txnId)
                        .isFalse();
                assertThat(txn.cardLast4()).hasSize(4);
            }
        }
    }
}
