package com.bino.dra.domain.model;

import java.util.Objects;

public record Money(long minorUnits, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency required (ISO-4217)");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
