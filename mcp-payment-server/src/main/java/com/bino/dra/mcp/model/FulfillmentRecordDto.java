package com.bino.dra.mcp.model;

/**
 * Shipping / delivery evidence — output of {@code get_fulfillment_record}. For a "goods not received"
 * dispute, {@code shipped=true} + tracking + {@code deliveryStatus=DELIVERED} is the representment case.
 */
public record FulfillmentRecordDto(
        String transactionId,
        boolean shipped,
        String shippedAt,
        String trackingRef,
        String deliveryStatus
) {
}
