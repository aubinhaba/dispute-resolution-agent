package com.bino.dra.domain.model;

import java.util.Objects;

// Minor units as long, never double: no floating-point rounding on money
public record Money(long minorUnits, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency required (ISO-4217)");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        // Sign left unconstrained on purpose: credits and refunds are negative
    }
}
