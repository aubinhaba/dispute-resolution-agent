package com.bino.dra.mcp.model;

public record TransactionSummaryDto(
        String transactionId,
        String merchantId,
        MoneyDto amount,
        String capturedAt,
        String scaResult
) {
    public static TransactionSummaryDto from(TransactionDto full) {
        return new TransactionSummaryDto(
                full.transactionId(), full.merchantId(), full.amount(),
                full.capturedAt(), full.scaResult());
    }
}
