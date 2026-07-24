package com.bino.dra.mcp.model;

/**
 * Full transaction and its risk signals — output of {@code get_transaction} and
 * {@code get_related_transactions}. Signals are Strings (the contract is JSON, the client has no
 * enums) and timestamps are ISO-8601. PCI/PII: {@code customerRef} is a token, never a PAN — only
 * {@code cardLast4}. The value vocabulary is documented in the tool descriptions.
 */
public record TransactionDto(
        String transactionId,
        String merchantId,
        String customerRef,
        MoneyDto amount,
        String capturedAt,
        String psp,
        String cardBrand,
        String cardLast4,
        String scaResult,
        String avsCheck,
        String cvvCheck,
        String ipCountry,
        String billingCountry
) {
}
