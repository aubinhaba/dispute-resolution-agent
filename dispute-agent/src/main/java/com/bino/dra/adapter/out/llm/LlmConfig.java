package com.bino.dra.adapter.out.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** LLM adapter configuration. Exposes a {@link Clock} bean so decision timestamps are testable. */
@Configuration
public class LlmConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
