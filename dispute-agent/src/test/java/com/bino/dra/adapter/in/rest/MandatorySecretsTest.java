package com.bino.dra.adapter.in.rest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MandatorySecretsTest {

    @Test
    void a_missing_key_prevents_startup() {
        assertThatThrownBy(() -> new ApiSecurityConfig(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRA_API_KEY");
    }

    @Test
    void an_empty_or_blank_key_does_not_count_as_a_key() {
        assertThatThrownBy(() -> new ApiSecurityConfig("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApiSecurityConfig("   ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_configured_key_allows_startup() {
        assertThatCode(() -> new ApiSecurityConfig("a-key")).doesNotThrowAnyException();
    }
}
