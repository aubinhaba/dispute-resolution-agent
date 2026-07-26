package com.bino.dra.mcp.model;

public record MoneyDto(long minorUnits, String currency, String formatted) {

    public static MoneyDto of(long minorUnits, String currency) {
        return new MoneyDto(minorUnits, currency,
                String.format("%d.%02d %s", minorUnits / 100, Math.abs(minorUnits % 100), currency));
    }
}
