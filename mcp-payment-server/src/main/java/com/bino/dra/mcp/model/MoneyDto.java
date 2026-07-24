package com.bino.dra.mcp.model;

/**
 * Monetary amount as exposed by the MCP tools. Integer minor units, never float. {@code formatted}
 * is a pre-computed display string ("45.00 EUR") so the model never does the conversion itself.
 */
public record MoneyDto(long minorUnits, String currency, String formatted) {

    public static MoneyDto of(long minorUnits, String currency) {
        return new MoneyDto(minorUnits, currency,
                String.format("%d.%02d %s", minorUnits / 100, Math.abs(minorUnits % 100), currency));
    }
}
