package com.bino.dra.domain.model;

import java.time.Instant;

public record FulfillmentRecord(
        String transactionId,
        boolean shipped,
        Instant shippedAt,
        String trackingRef,
        String deliveryStatus
) {
}
