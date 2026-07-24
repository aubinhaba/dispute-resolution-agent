package com.bino.dra.mcp.model;

/**
 * Trimmed transaction — output of {@code get_customer_history}. Returns a summary rather than the full
 * {@link TransactionDto} to keep the agent's context budget small; the full record stays one
 * {@code get_transaction} call away.
 */
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
