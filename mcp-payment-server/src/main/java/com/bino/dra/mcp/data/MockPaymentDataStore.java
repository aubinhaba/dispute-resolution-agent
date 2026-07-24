package com.bino.dra.mcp.data;

import com.bino.dra.mcp.model.FulfillmentRecordDto;
import com.bino.dra.mcp.model.MoneyDto;
import com.bino.dra.mcp.model.TransactionDto;
import com.bino.dra.mcp.model.TransactionSummaryDto;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory stand-in for a PSP/OMS backend. The dataset materializes the four eval cases (see
 * {@code dispute-agent/src/test/resources/eval/eval-cases-01.md}); the day a real backend is wired in,
 * only this class changes. Read-only by construction: immutable collections, no write method.
 * Timestamps are relative to an injected clock so the history window stays testable over real time.
 */
@Component
public class MockPaymentDataStore {

    private static final String SCA_AUTHENTICATED = "AUTHENTICATED";
    private static final String SCA_ATTEMPTED = "ATTEMPTED";
    private static final String SCA_NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    private static final String MATCH = "MATCH";
    private static final String MISMATCH = "MISMATCH";

    private final Clock clock;
    private final Map<String, TransactionDto> transactionsById;
    private final Map<String, List<String>> historyByCustomer;
    private final Map<String, List<String>> relatedByTransaction;
    private final Map<String, FulfillmentRecordDto> fulfillmentByTransaction;

    public MockPaymentDataStore(Clock clock) {
        this.clock = clock;
        Instant now = clock.instant();

        List<TransactionDto> all = List.of(
                // EVAL-001 (-> ACCEPT): recent customer, failed 3DS, inconsistent geography.
                txn("TXN-EVAL-001", "MERCH-ELEC-01", "CUST-7Q2F9", 4500, daysAgo(now, 15),
                        "STRIPE", "VISA", "4242", SCA_NOT_AUTHENTICATED, MISMATCH, MATCH, "NG", "FR"),

                // EVAL-002 (-> REPRESENT): alleged fraud but every signal green, loyal customer + history.
                txn("TXN-EVAL-002", "MERCH-ELEC-01", "CUST-M4XA1", 12000, daysAgo(now, 20),
                        "STRIPE", "VISA", "1881", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
                txn("TXN-H-M4XA1-1", "MERCH-ELEC-01", "CUST-M4XA1", 7900, daysAgo(now, 82),
                        "STRIPE", "VISA", "1881", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
                txn("TXN-H-M4XA1-2", "MERCH-ELEC-01", "CUST-M4XA1", 15600, daysAgo(now, 61),
                        "STRIPE", "VISA", "1881", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
                txn("TXN-H-M4XA1-3", "MERCH-ELEC-01", "CUST-M4XA1", 4300, daysAgo(now, 44),
                        "STRIPE", "VISA", "1881", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
                txn("TXN-H-M4XA1-4", "MERCH-ELEC-01", "CUST-M4XA1", 9900, daysAgo(now, 33),
                        "STRIPE", "VISA", "1881", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),

                // EVAL-003 (-> REPRESENT): "goods not received" but delivered (see fulfillment).
                txn("TXN-EVAL-003", "MERCH-FASHION-02", "CUST-K9PT3", 8000, daysAgo(now, 30),
                        "ADYEN", "VISA", "0119", SCA_ATTEMPTED, MATCH, MATCH, "FR", "FR"),
                txn("TXN-H-K9PT3-1", "MERCH-FASHION-02", "CUST-K9PT3", 6200, daysAgo(now, 75),
                        "ADYEN", "VISA", "0119", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),

                // EVAL-004 (-> ESCALATE): 1,500.00 EUR, above threshold — the amount decides.
                txn("TXN-EVAL-004", "MERCH-LUX-03", "CUST-B2RH8", 150000, daysAgo(now, 10),
                        "WORLDLINE", "VISA", "7005", SCA_AUTHENTICATED, MATCH, MATCH, "DE", "DE"),
                txn("TXN-H-B2RH8-1", "MERCH-LUX-03", "CUST-B2RH8", 89000, daysAgo(now, 55),
                        "WORLDLINE", "VISA", "7005", SCA_AUTHENTICATED, MATCH, MATCH, "DE", "DE"));

        this.transactionsById = all.stream()
                .collect(Collectors.toUnmodifiableMap(TransactionDto::transactionId, t -> t));

        // Derived from the data, not hand-maintained, to avoid drift between tools.
        this.historyByCustomer = all.stream()
                .collect(Collectors.groupingBy(TransactionDto::customerRef,
                        Collectors.mapping(TransactionDto::transactionId, Collectors.toUnmodifiableList())));

        this.relatedByTransaction = Map.of(
                "TXN-EVAL-001", List.of(),
                "TXN-EVAL-002", List.of("TXN-H-M4XA1-1", "TXN-H-M4XA1-2", "TXN-H-M4XA1-3", "TXN-H-M4XA1-4"),
                "TXN-EVAL-003", List.of("TXN-H-K9PT3-1"),
                "TXN-EVAL-004", List.of("TXN-H-B2RH8-1"));

        // Only physical goods have a record; its absence for EVAL-001/002 (digital) is legitimate.
        this.fulfillmentByTransaction = Map.of(
                "TXN-EVAL-003", new FulfillmentRecordDto("TXN-EVAL-003", true,
                        iso(daysAgo(now, 28)), "TRK-FR-88123901", "DELIVERED"),
                "TXN-EVAL-004", new FulfillmentRecordDto("TXN-EVAL-004", true,
                        iso(daysAgo(now, 7)), "TRK-DE-55201148", "DELIVERED"));
    }

    public Optional<TransactionDto> findTransaction(String transactionId) {
        return Optional.ofNullable(transactionsById.get(transactionId));
    }

    public boolean knowsCustomer(String customerRef) {
        return historyByCustomer.containsKey(customerRef);
    }

    /** History within the {@code lookbackDays} window, most recent first, capped and summarized. */
    public List<TransactionSummaryDto> customerHistory(String customerRef, int lookbackDays, int limit) {
        Instant cutoff = clock.instant().minus(lookbackDays, ChronoUnit.DAYS);
        return historyByCustomer.getOrDefault(customerRef, List.of()).stream()
                .map(transactionsById::get)
                .filter(t -> Instant.parse(t.capturedAt()).isAfter(cutoff))
                .sorted(Comparator.comparing(TransactionDto::capturedAt).reversed())
                .limit(limit)
                .map(TransactionSummaryDto::from)
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<List<TransactionDto>> relatedTransactions(String transactionId) {
        List<String> ids = relatedByTransaction.get(transactionId);
        if (ids == null && !transactionsById.containsKey(transactionId)) {
            return Optional.empty(); // unknown transaction ≠ "no related"
        }
        List<String> safeIds = ids == null ? List.of() : ids;
        return Optional.of(safeIds.stream()
                .map(transactionsById::get)
                .collect(Collectors.toUnmodifiableList()));
    }

    public Optional<FulfillmentRecordDto> findFulfillment(String transactionId) {
        return Optional.ofNullable(fulfillmentByTransaction.get(transactionId));
    }

    public boolean knowsTransaction(String transactionId) {
        return transactionsById.containsKey(transactionId);
    }

    private static TransactionDto txn(String id, String merchant, String customerRef, long minorUnits,
                                      Instant capturedAt, String psp, String brand, String last4,
                                      String sca, String avs, String cvv, String ipCountry, String billingCountry) {
        return new TransactionDto(id, merchant, customerRef, MoneyDto.of(minorUnits, "EUR"),
                iso(capturedAt), psp, brand, last4, sca, avs, cvv, ipCountry, billingCountry);
    }

    private static Instant daysAgo(Instant now, int days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private static String iso(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
