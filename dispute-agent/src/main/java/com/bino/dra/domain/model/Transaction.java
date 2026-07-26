package com.bino.dra.domain.model;

import java.time.Instant;

// PCI: customerRef is a token and cardLast4 the only card data — a full PAN never enters this record
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
