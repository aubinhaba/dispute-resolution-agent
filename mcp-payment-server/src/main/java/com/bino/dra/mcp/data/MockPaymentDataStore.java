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

@Component
public class MockPaymentDataStore {

    private static final String SCA_AUTHENTICATED = "AUTHENTICATED";
    private static final String SCA_ATTEMPTED = "ATTEMPTED";
    private static final String SCA_NOT_AUTHENTICATED = "NOT_AUTHENTICATED";
    private static final String MATCH = "MATCH";
    private static final String MISMATCH = "MISMATCH";
    private static final String NOT_CHECKED = "NOT_CHECKED";

    private final Clock clock;
    private final Map<String, TransactionDto> transactionsById;
    private final Map<String, List<String>> historyByCustomer;
    private final Map<String, List<String>> relatedByTransaction;
    private final Map<String, FulfillmentRecordDto> fulfillmentByTransaction;

    public MockPaymentDataStore(Clock clock) {
        this.clock = clock;
        Instant now = clock.instant();

        List<TransactionDto> all = transactions(now);

        this.transactionsById = all.stream()
                .collect(Collectors.toUnmodifiableMap(TransactionDto::transactionId, t -> t));

        this.historyByCustomer = all.stream()
                .collect(Collectors.groupingBy(TransactionDto::customerRef,
                        Collectors.mapping(TransactionDto::transactionId, Collectors.toUnmodifiableList())));

        this.relatedByTransaction = relatedTransactions();
        this.fulfillmentByTransaction = fulfillment(now);
    }

    private static List<TransactionDto> transactions(Instant now) {
        return List.of(
            txn("TXN-EVAL-001", "MERCH-ELEC-01", "CUST-7Q2F9", 4500, daysAgo(now, 15),
                    "STRIPE", "VISA", "4242", SCA_NOT_AUTHENTICATED, MISMATCH, MATCH, "NG", "FR"),

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

            txn("TXN-EVAL-003", "MERCH-FASHION-02", "CUST-K9PT3", 8000, daysAgo(now, 30),
                    "ADYEN", "VISA", "0119", SCA_ATTEMPTED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-H-K9PT3-1", "MERCH-FASHION-02", "CUST-K9PT3", 6200, daysAgo(now, 75),
                    "ADYEN", "VISA", "0119", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),

            txn("TXN-EVAL-004", "MERCH-LUX-03", "CUST-B2RH8", 150000, daysAgo(now, 10),
                    "WORLDLINE", "VISA", "7005", SCA_AUTHENTICATED, MATCH, MATCH, "DE", "DE"),
            txn("TXN-H-B2RH8-1", "MERCH-LUX-03", "CUST-B2RH8", 89000, daysAgo(now, 55),
                    "WORLDLINE", "VISA", "7005", SCA_AUTHENTICATED, MATCH, MATCH, "DE", "DE"),

            txn("TXN-EVAL-005", "MERCH-ELEC-01", "CUST-T8LV4", 9500, daysAgo(now, 18),
                    "STRIPE", "MASTERCARD", "5309", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-H-T8LV4-1", "MERCH-ELEC-01", "CUST-T8LV4", 4200, daysAgo(now, 66),
                    "STRIPE", "MASTERCARD", "5309", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-H-T8LV4-2", "MERCH-ELEC-01", "CUST-T8LV4", 11300, daysAgo(now, 39),
                    "STRIPE", "MASTERCARD", "5309", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),

            txn("TXN-EVAL-006", "MERCH-FASHION-02", "CUST-W3ND6", 6700, daysAgo(now, 25),
                    "ADYEN", "MASTERCARD", "2647", SCA_ATTEMPTED, MATCH, NOT_CHECKED, "FR", "FR"),

            txn("TXN-EVAL-007", "MERCH-ELEC-01", "CUST-R5DQ2", 6200, daysAgo(now, 12),
                    "STRIPE", "VISA", "3391", SCA_ATTEMPTED, MISMATCH, MISMATCH, "NG", "FR"),
            txn("TXN-EVAL-008", "MERCH-ELEC-01", "CUST-J7KE5", 3400, daysAgo(now, 16),
                    "STRIPE", "VISA", "8820", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-H-J7KE5-1", "MERCH-ELEC-01", "CUST-J7KE5", 2900, daysAgo(now, 58),
                    "STRIPE", "VISA", "8820", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-EVAL-009", "MERCH-FASHION-02", "CUST-P2WS8", 5500, daysAgo(now, 22),
                    "ADYEN", "VISA", "6714", SCA_ATTEMPTED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-EVAL-010", "MERCH-FASHION-02", "CUST-V4HM7", 7300, daysAgo(now, 27),
                    "ADYEN", "VISA", "1052", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-EVAL-011", "MERCH-ELEC-01", "CUST-Z9BF1", 8100, daysAgo(now, 14),
                    "STRIPE", "MASTERCARD", "4478", SCA_NOT_AUTHENTICATED, MISMATCH, MATCH, "RO", "FR"),
            txn("TXN-EVAL-012", "MERCH-FASHION-02", "CUST-D6NK4", 4900, daysAgo(now, 31),
                    "ADYEN", "MASTERCARD", "9163", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-H-D6NK4-1", "MERCH-FASHION-02", "CUST-D6NK4", 3600, daysAgo(now, 70),
                    "ADYEN", "MASTERCARD", "9163", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-EVAL-013", "MERCH-FASHION-02", "CUST-L1TG9", 5200, daysAgo(now, 19),
                    "ADYEN", "MASTERCARD", "7735", SCA_ATTEMPTED, NOT_CHECKED, NOT_CHECKED, "FR", "FR"),
            txn("TXN-EVAL-014", "MERCH-FASHION-02", "CUST-X8CR6", 9100, daysAgo(now, 29),
                    "ADYEN", "MASTERCARD", "5501", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),

            txn("TXN-EVAL-015", "MERCH-LUX-03", "CUST-N3QY7", 250000, daysAgo(now, 8),
                    "WORLDLINE", "VISA", "6640", SCA_AUTHENTICATED, MATCH, MATCH, "FR", "FR"),
            txn("TXN-EVAL-016", "MERCH-LUX-03", "CUST-H5JB2", 180000, daysAgo(now, 11),
                    "WORLDLINE", "MASTERCARD", "3308", SCA_AUTHENTICATED, MATCH, MATCH, "DE", "DE"));
    }

    private static Map<String, List<String>> relatedTransactions() {
        return Map.of(
            "TXN-EVAL-001", List.of(),
            "TXN-EVAL-002", List.of("TXN-H-M4XA1-1", "TXN-H-M4XA1-2", "TXN-H-M4XA1-3", "TXN-H-M4XA1-4"),
            "TXN-EVAL-003", List.of("TXN-H-K9PT3-1"),
            "TXN-EVAL-004", List.of("TXN-H-B2RH8-1"),
            "TXN-EVAL-005", List.of("TXN-H-T8LV4-1", "TXN-H-T8LV4-2"),
            "TXN-EVAL-006", List.of(),
            "TXN-EVAL-008", List.of("TXN-H-J7KE5-1"),
            "TXN-EVAL-012", List.of("TXN-H-D6NK4-1"));
    }

    private static Map<String, FulfillmentRecordDto> fulfillment(Instant now) {
        return Map.of(
            "TXN-EVAL-003", new FulfillmentRecordDto("TXN-EVAL-003", true,
                    iso(daysAgo(now, 28)), "TRK-FR-88123901", "DELIVERED"),
            "TXN-EVAL-004", new FulfillmentRecordDto("TXN-EVAL-004", true,
                    iso(daysAgo(now, 7)), "TRK-DE-55201148", "DELIVERED"),
            "TXN-EVAL-009", new FulfillmentRecordDto("TXN-EVAL-009", true,
                    iso(daysAgo(now, 20)), "TRK-FR-77410256", "IN_TRANSIT"),
            "TXN-EVAL-010", new FulfillmentRecordDto("TXN-EVAL-010", true,
                    iso(daysAgo(now, 25)), "TRK-FR-31905744", "DELIVERED"),
            "TXN-EVAL-012", new FulfillmentRecordDto("TXN-EVAL-012", true,
                    iso(daysAgo(now, 29)), "TRK-FR-60238815", "DELIVERED"),
            "TXN-EVAL-014", new FulfillmentRecordDto("TXN-EVAL-014", true,
                    iso(daysAgo(now, 27)), "TRK-FR-49572630", "DELIVERED"));
    }

    public Optional<TransactionDto> findTransaction(String transactionId) {
        return Optional.ofNullable(transactionsById.get(transactionId));
    }

    public boolean knowsCustomer(String customerRef) {
        return historyByCustomer.containsKey(customerRef);
    }

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
