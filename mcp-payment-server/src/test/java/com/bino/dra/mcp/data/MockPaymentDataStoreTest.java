package com.bino.dra.mcp.data;

import com.bino.dra.mcp.model.TransactionDto;
import com.bino.dra.mcp.model.TransactionSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Store tests — verify the dataset faithfully materializes the four eval-case specs, so a broken value is
 * caught here rather than as a false regression in the eval harness. Clock frozen for stable windows.
 */
class MockPaymentDataStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private MockPaymentDataStore store;

    @BeforeEach
    void setUp() {
        store = new MockPaymentDataStore(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Consistency with the eval-case specs")
    class EvalCaseConsistency {

        @Test
        @DisplayName("EVAL-001 (-> ACCEPT): failed 3DS, AVS mismatch, inconsistent geography, 45.00 EUR")
        void eval001_hasWeakSignals() {
            TransactionDto txn = store.findTransaction("TXN-EVAL-001").orElseThrow();

            assertThat(txn.scaResult()).isEqualTo("NOT_AUTHENTICATED");
            assertThat(txn.avsCheck()).isEqualTo("MISMATCH");
            assertThat(txn.amount().minorUnits()).isEqualTo(4500);
            assertThat(txn.amount().formatted()).isEqualTo("45.00 EUR");
            assertThat(txn.ipCountry()).isNotEqualTo(txn.billingCountry());
        }

        @Test
        @DisplayName("EVAL-002 (-> REPRESENT): 3DS authenticated (liability shift), green signals, 120.00 EUR")
        void eval002_hasStrongSignals() {
            TransactionDto txn = store.findTransaction("TXN-EVAL-002").orElseThrow();

            assertThat(txn.scaResult()).isEqualTo("AUTHENTICATED");
            assertThat(txn.avsCheck()).isEqualTo("MATCH");
            assertThat(txn.cvvCheck()).isEqualTo("MATCH");
            assertThat(txn.amount().minorUnits()).isEqualTo(12000);
            assertThat(txn.ipCountry()).isEqualTo(txn.billingCountry());
        }

        @Test
        @DisplayName("EVAL-002: the loyal customer has a rich history + related transactions (same card)")
        void eval002_customerLooksLoyal() {
            TransactionDto txn = store.findTransaction("TXN-EVAL-002").orElseThrow();

            List<TransactionSummaryDto> history = store.customerHistory(txn.customerRef(), 90, 20);
            // 4 historical purchases + the disputed transaction itself, all < 90 days.
            assertThat(history).hasSize(5);

            List<TransactionDto> related = store.relatedTransactions("TXN-EVAL-002").orElseThrow();
            assertThat(related).isNotEmpty()
                    .allSatisfy(r -> assertThat(r.cardLast4()).isEqualTo(txn.cardLast4()));
        }

        @Test
        @DisplayName("EVAL-003 (-> REPRESENT): 'not received' claim but full delivery proof")
        void eval003_hasDeliveryProof() {
            var fulfillment = store.findFulfillment("TXN-EVAL-003").orElseThrow();

            assertThat(fulfillment.shipped()).isTrue();
            assertThat(fulfillment.trackingRef()).isNotBlank();
            assertThat(fulfillment.deliveryStatus()).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("EVAL-004 (-> ESCALATE): 1,500.00 EUR — above the deterministic 1,000 EUR threshold")
        void eval004_isAboveEscalationThreshold() {
            TransactionDto txn = store.findTransaction("TXN-EVAL-004").orElseThrow();

            // Threshold: minorUnits > 100000 -> ESCALATE (a non-LLM rule).
            assertThat(txn.amount().minorUnits()).isGreaterThan(100_000L);
        }

        @Test
        @DisplayName("EVAL-001/002 (digital goods): no fulfillment record — a legitimate absence")
        void digitalGoods_haveNoFulfillment() {
            assertThat(store.findFulfillment("TXN-EVAL-001")).isEmpty();
            assertThat(store.findFulfillment("TXN-EVAL-002")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Customer history semantics")
    class CustomerHistory {

        @Test
        @DisplayName("lookbackDays filter: a 50-day window excludes older purchases")
        void lookbackWindow_filtersOldTransactions() {
            // CUST-M4XA1: purchases at D-82, D-61, D-44, D-33 and D-20 (the disputed one).
            List<TransactionSummaryDto> recent = store.customerHistory("CUST-M4XA1", 50, 20);

            assertThat(recent).extracting(TransactionSummaryDto::transactionId)
                    .containsExactly("TXN-EVAL-002", "TXN-H-M4XA1-4", "TXN-H-M4XA1-3");
        }

        @Test
        @DisplayName("limit caps the number of entries, most recent first")
        void limit_capsResults() {
            List<TransactionSummaryDto> capped = store.customerHistory("CUST-M4XA1", 90, 2);

            assertThat(capped).hasSize(2);
            assertThat(capped.getFirst().transactionId()).isEqualTo("TXN-EVAL-002");
        }

        @Test
        @DisplayName("known customer with no activity in the window -> empty list (signal, not error)")
        void knownCustomer_quietWindow_returnsEmpty() {
            // CUST-B2RH8: purchases at D-10 and D-55 -> a 5-day window contains nothing.
            assertThat(store.customerHistory("CUST-B2RH8", 5, 20)).isEmpty();
        }
    }

    @Nested
    @DisplayName("'Unknown' vs 'empty' nuance (anti-hallucination)")
    class UnknownVersusEmpty {

        @Test
        @DisplayName("unknown transaction -> Optional.empty (the tool turns it into an MCP error)")
        void unknownTransaction_isEmpty() {
            assertThat(store.findTransaction("TXN-DOES-NOT-EXIST")).isEmpty();
            assertThat(store.relatedTransactions("TXN-DOES-NOT-EXIST")).isEmpty();
        }

        @Test
        @DisplayName("known transaction with no related -> empty list (a legitimate answer, not an error)")
        void knownTransaction_withoutRelated_returnsEmptyList() {
            assertThat(store.relatedTransactions("TXN-EVAL-001")).contains(List.of());
        }
    }
}
