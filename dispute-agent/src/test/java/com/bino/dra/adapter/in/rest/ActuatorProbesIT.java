package com.bino.dra.adapter.in.rest;

import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@NoDatabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.anthropic.api-key=not-used-by-this-test")
class ActuatorProbesIT {

    @LocalServerPort
    private int port;

    @Autowired
    private VectorStore ruleVectorStore;

    @Test
    void both_probes_exist_and_answer_UP() {
        assertThat(probe("/actuator/health/readiness")).contains("\"status\":\"UP\"");
        assertThat(probe("/actuator/health/liveness")).contains("\"status\":\"UP\"");
    }

    @Test
    void when_readiness_is_UP_the_corpus_really_is_indexed() {
        assertThat(probe("/actuator/health/readiness")).contains("\"status\":\"UP\"");

        assertThat(ruleVectorStore.similaritySearch(
                SearchRequest.builder().query("chargeback representment deadline").topK(1).build()))
                .isNotEmpty();
    }

    @Test
    void no_other_actuator_endpoint_is_exposed_even_to_an_authenticated_caller() {
        assertThat(statusWithKey("/actuator/env")).isEqualTo(404);
        assertThat(statusWithKey("/actuator/beans")).isEqualTo(404);
    }

    @Test
    void a_caller_without_a_key_cannot_tell_a_missing_endpoint_from_a_protected_one() {
        assertThat(status("/actuator/env")).isEqualTo(401);
        assertThat(status("/disputes/whatever")).isEqualTo(401);
    }

    private String probe(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .retrieve().body(String.class);
    }

    private int status(String path) {
        return RestClient.create("http://localhost:" + port).get().uri(path)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private int statusWithKey(String path) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", System.getProperty("dra.security.api-key", ""))
                .build()
                .get().uri(path)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
