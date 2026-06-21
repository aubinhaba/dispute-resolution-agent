package com.bino.dra.domain.model;

import java.time.Instant;

/**
 * Order fulfillment proof (shipping / delivery) — the "goods not received" path.
 *
 * <p>For a "not received" reason code, delivery proof is the decisive REPRESENT argument. Optional
 * upstream: not every transaction has a logistics leg (online services ship nothing).
 */
public record FulfillmentRecord(
        String transactionId,
        boolean shipped,
        Instant shippedAt,
        String trackingRef,
        String deliveryStatus
) {
}
