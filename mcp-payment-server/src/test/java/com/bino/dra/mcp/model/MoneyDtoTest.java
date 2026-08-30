package com.bino.dra.mcp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyDtoTest {

    @Test
    @DisplayName("a positive amount formats with two decimals")
    void positiveAmount_formatsWithTwoDecimals() {
        assertThat(MoneyDto.of(4500, "EUR").formatted()).isEqualTo("45.00 EUR");
    }

    @Test
    @DisplayName("a sub-unit credit keeps its sign")
    void subUnitCredit_keepsTheMinusSign() {
        assertThat(MoneyDto.of(-50, "EUR").formatted()).isEqualTo("-0.50 EUR");
    }

    @Test
    @DisplayName("a credit above one unit keeps its sign")
    void credit_keepsTheMinusSign() {
        assertThat(MoneyDto.of(-12045, "EUR").formatted()).isEqualTo("-120.45 EUR");
    }

    @Test
    @DisplayName("zero carries no sign")
    void zero_isUnsigned() {
        assertThat(MoneyDto.of(0, "EUR").formatted()).isEqualTo("0.00 EUR");
    }
}
