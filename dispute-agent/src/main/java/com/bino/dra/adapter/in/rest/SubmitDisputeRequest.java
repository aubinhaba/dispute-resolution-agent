package com.bino.dra.adapter.in.rest;

import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;

import java.time.Instant;

public record SubmitDisputeRequest(
        String disputeId,
        String transactionId,
        String merchantId,
        String network,
        String reasonCode,
        Long disputedAmountMinorUnits,
        String currency,
        Instant raisedAt,
        Instant representmentDueBy,
        String issuerClaim
) {
    public Dispute toDomain() {
        return new Dispute(
                required(disputeId, "disputeId"),
                required(transactionId, "transactionId"),
                merchantId,
                parsedNetwork(),
                required(reasonCode, "reasonCode"),
                amount(),
                raisedAt,
                representmentDueBy,
                issuerClaim);
    }

    private Network parsedNetwork() {
        String value = required(network, "network");
        try {
            return Network.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "network must be VISA or MASTERCARD, received: " + value);
        }
    }

    private Money amount() {
        if (disputedAmountMinorUnits == null) {
            throw new IllegalArgumentException("disputedAmountMinorUnits required");
        }
        return new Money(disputedAmountMinorUnits, required(currency, "currency"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        return value;
    }
}
