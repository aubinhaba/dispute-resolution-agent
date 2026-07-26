package com.bino.dra.mcp.model;

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
