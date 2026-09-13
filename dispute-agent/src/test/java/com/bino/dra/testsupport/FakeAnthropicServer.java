package com.bino.dra.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

// Replaces the network, not Spring AI: the real AnthropicChatModel, its observations and the
// advisor chain all run. A mocked ChatClient would emit no observation and prove nothing
public final class FakeAnthropicServer implements AutoCloseable {

    public record Request(String path, String body) { }

    private record CannedResponse(int status, String body) { }

    private static final String ERROR = """
            {"type":"error","error":{"type":"api_error","message":"%s"}}""";

    private final HttpServer server;
    private final Queue<CannedResponse> responses = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();

    private FakeAnthropicServer(HttpServer server) {
        this.server = server;
    }

    public static FakeAnthropicServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakeAnthropicServer fake = new FakeAnthropicServer(server);
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public FakeAnthropicServer answerText(String text, int inputTokens, int outputTokens) {
        String content = """
                [{"type":"text","text":"%s"}]""".formatted(text);
        return enqueue(200, message(content, "end_turn", inputTokens, outputTokens));
    }

    public FakeAnthropicServer answerToolUse(String tool, int inputTokens, int outputTokens) {
        String content = """
                [{"type":"tool_use","id":"toolu_%d","name":"%s","input":{}}]"""
                .formatted(ids.incrementAndGet(), tool);
        return enqueue(200, message(content, "tool_use", inputTokens, outputTokens));
    }

    public FakeAnthropicServer answerStatus(int status) {
        return enqueue(status, ERROR.formatted("simulated outage"));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        responses.clear();
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private FakeAnthropicServer enqueue(int status, String body) {
        responses.add(new CannedResponse(status, body));
        return this;
    }

    private String message(String content, String stopReason, int inputTokens, int outputTokens) {
        return """
                {"id":"msg_%d","type":"message","role":"assistant","model":"claude-haiku-4-5",
                 "content":%s,"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":%d,"output_tokens":%d}}"""
                .formatted(ids.incrementAndGet(), content, stopReason, inputTokens, outputTokens);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(new Request(exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        CannedResponse response = responses.poll();
        // An unplanned call is a test mistake: a 400 is not retried, so it surfaces immediately
        if (response == null) {
            response = new CannedResponse(400, ERROR.formatted("no canned response prepared"));
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
