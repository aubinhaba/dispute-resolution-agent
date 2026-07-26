package com.bino.dra.adapter.out.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LlmConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
