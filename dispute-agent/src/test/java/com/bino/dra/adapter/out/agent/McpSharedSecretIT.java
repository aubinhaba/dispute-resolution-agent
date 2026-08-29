package com.bino.dra.adapter.out.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

// A server accepting everyone would leave McpToolDiscoveryIT green: this refuses the anonymous
class McpSharedSecretIT {

    private static final String MCP_URL = "http://localhost:8081/mcp";

    @Test
    void a_caller_without_the_shared_secret_is_refused() {
        assertThat(status(null)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void a_wrong_shared_secret_is_refused_like_a_missing_one() {
        assertThat(status("not-the-right-secret")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void the_health_probe_stays_reachable_without_a_secret() {
        var status = RestClient.create().get()
                .uri("http://localhost:8081/actuator/health")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.OK);
    }

    private static Object status(String secret) {
        var request = RestClient.create().post().uri(MCP_URL)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (secret != null) {
            request = request.header("X-MCP-Secret", secret);
        }
        return request.body("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
                .exchange((req, response) -> response.getStatusCode());
    }
}
