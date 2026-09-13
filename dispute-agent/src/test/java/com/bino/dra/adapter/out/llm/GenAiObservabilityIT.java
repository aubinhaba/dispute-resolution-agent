package com.bino.dra.adapter.out.llm;

import com.bino.dra.testsupport.FakeAnthropicServer;
import com.bino.dra.testsupport.NoDatabase;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Every link of the chain, proved without a key: model call, Observation, counter, OTLP export
@NoDatabase
@SpringBootTest(properties = {
        "spring.ai.anthropic.api-key=fake-key-never-sent-to-anthropic",
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.step=1s"})
class GenAiObservabilityIT {

    private static final String TOKENS = "gen_ai.client.token.usage";

    private static final FakeAnthropicServer anthropic = FakeAnthropicServer.start();
    private static final OtlpCollector collector = OtlpCollector.start();

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.base-url", anthropic::baseUrl);
        registry.add("management.otlp.metrics.export.url", collector::url);
    }

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterAll
    static void stop() {
        anthropic.close();
        collector.close();
    }

    @BeforeEach
    void reset() {
        anthropic.reset();
    }

    @Test
    void every_round_of_the_tool_loop_feeds_the_gen_ai_counter() {
        double inputBefore = tokens("input");
        double outputBefore = tokens("output");
        anthropic.answerToolUse("lookup", 100, 20).answerText("done", 150, 30);

        ChatResponse response = chatClientBuilder.build().prompt()
                .user("dispute").tools(new FakeTool()).call().chatResponse();

        assertThat(anthropic.requests()).hasSize(2);
        assertThat(tokens("input") - inputBefore).isEqualTo(250);
        assertThat(tokens("output") - outputBefore).isEqualTo(50);
        // What motivates TokenBudgetAdvisor: the final response carries only the last round
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(150);
    }

    @Test
    void the_counter_carries_the_semconv_tags_and_no_dispute_identifier() {
        anthropic.answerText("ok", 10, 5);

        chatClientBuilder.build().prompt().user("dispute").call().content();

        // Filtering on the operation is mandatory: the local ONNX embedding model emits the same
        // metric with gen_ai.system=onnx, so an unfiltered sum counts free tokens as billed ones
        Counter counter = meterRegistry.get(TOKENS)
                .tag("gen_ai.operation.name", "chat").tag("gen_ai.token.type", "input").counter();
        assertThat(counter.getId().getTag("gen_ai.system")).isEqualTo("anthropic");
        assertThat(counter.getId().getTag("gen_ai.request.model")).isEqualTo("claude-haiku-4-5");
        assertThat(counter.getId().getTags()).extracting(Tag::getKey)
                .noneMatch(key -> key.toLowerCase().contains("dispute"));
    }

    @Test
    void the_per_call_output_bound_reaches_the_wire() {
        anthropic.answerText("ok", 10, 5);

        chatClientBuilder.build().prompt().user("dispute").call().content();

        assertThat(anthropic.requests().getFirst().body()).contains("\"max_tokens\":2048");
    }

    @Test
    void the_otlp_export_pushes_the_gen_ai_counter_to_a_collector() {
        anthropic.answerText("ok", 10, 5);

        chatClientBuilder.build().prompt().user("dispute").call().content();

        await().atMost(Duration.ofSeconds(15)).until(() -> collector.received(TOKENS));
    }

    private double tokens(String type) {
        return meterRegistry.find(TOKENS).tag("gen_ai.token.type", type).counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    static class FakeTool {
        @Tool(description = "Test-only tool whose sole purpose is to force a second round")
        String lookup() {
            return "ok";
        }
    }

    // Minimal OTLP receiver: protobuf carries metric names as readable UTF-8
    private static final class OtlpCollector implements AutoCloseable {

        private final HttpServer server;
        private final List<byte[]> bodies = new CopyOnWriteArrayList<>();

        private OtlpCollector(HttpServer server) {
            this.server = server;
        }

        static OtlpCollector start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                OtlpCollector collector = new OtlpCollector(server);
                server.createContext("/", exchange -> {
                    collector.bodies.add(exchange.getRequestBody().readAllBytes());
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
                server.start();
                return collector;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort() + "/v1/metrics";
        }

        boolean received(String metricName) {
            return bodies.stream().anyMatch(
                    body -> new String(body, StandardCharsets.ISO_8859_1).contains(metricName));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
