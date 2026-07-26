package com.bino.dra.mcp.model;

public record FulfillmentRecordDto(
        String transactionId,
        boolean shipped,
        String shippedAt,
        String trackingRef,
        String deliveryStatus
) {
}
