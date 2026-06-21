package com.bino.dra.domain.model;

import java.time.Instant;

/**
 * Payment transaction and its risk signals — the core of the evidence bundle.
 *
 * <p>PII/PCI: {@code customerRef} is a token (never a cleartext identity) and the full PAN is never
 * present — only {@code cardLast4}. The {@code sca}/{@code avs}/{@code cvv} and IP-vs-billing country
 * mismatch are the signals driving REPRESENT/ACCEPT.
 */
public record Transaction(
        String transactionId,
        String merchantId,
        String customerRef,
        Money amount,
        Instant capturedAt,
        String psp,
        String cardBrand,
        String cardLast4,
        ScaResult sca,
        CheckResult avs,
        CheckResult cvv,
        String ipCountry,
        String billingCountry
) {
}
