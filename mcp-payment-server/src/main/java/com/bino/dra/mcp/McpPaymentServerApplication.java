package com.bino.dra.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * MCP payment data server. Exposes read-only tools over STDIO; it never calls a model. Note the STDIO
 * constraint: stdout carries the JSON-RPC protocol, so the banner and console logging are disabled in
 * {@code application.yml} — a stray print would break the handshake.
 */
@SpringBootApplication
public class McpPaymentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpPaymentServerApplication.class, args);
    }

    /** Injectable clock so tests can freeze time for the {@code get_customer_history} window. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
