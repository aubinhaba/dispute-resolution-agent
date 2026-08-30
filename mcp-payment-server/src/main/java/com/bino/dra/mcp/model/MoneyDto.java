package com.bino.dra.mcp.model;

public record MoneyDto(long minorUnits, String currency, String formatted) {

    private static final long MINOR_UNITS_PER_UNIT = 100L;

    public static MoneyDto of(long minorUnits, String currency) {
        return new MoneyDto(minorUnits, currency, format(minorUnits, currency));
    }

    private static String format(long minorUnits, String currency) {
        long magnitude = Math.abs(minorUnits);
        return String.format("%s%d.%02d %s",
                minorUnits < 0 ? "-" : "",
                magnitude / MINOR_UNITS_PER_UNIT,
                magnitude % MINOR_UNITS_PER_UNIT,
                currency);
    }
}
