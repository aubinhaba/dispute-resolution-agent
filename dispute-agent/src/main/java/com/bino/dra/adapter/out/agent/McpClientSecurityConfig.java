package com.bino.dra.adapter.out.agent;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// The transport bean has no @ConditionalOnMissingBean, so this uses the framework extension
// point rather than replacing it and owning connection parsing (ADR-0020)
public class McpClientSecurityConfig {

    static final String HEADER = "X-MCP-Secret";

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> mcpSharedSecret(
            @Value("${dra.mcp.shared-secret:}") String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException("""
                    dra.mcp.shared-secret is empty: the agent refuses to start.
                    Set DRA_MCP_SHARED_SECRET (see .env.example) to the same value as the
                    MCP server.""");
        }

        return (connectionName, transport) -> transport.httpRequestCustomizer(
                (request, method, uri, body, context) -> request.header(HEADER, sharedSecret));
    }
}
