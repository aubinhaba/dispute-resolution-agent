package com.bino.dra.domain.model;

import java.util.Objects;

/**
 * Monetary amount in minor units (cents). Uses {@code long}, never {@code double}, to avoid
 * floating-point rounding on money. Immutable value object.
 */
public record Money(long minorUnits, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency required (ISO-4217)");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        // Sign is intentionally unconstrained: credits/refunds may be negative.
    }
}
