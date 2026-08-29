package com.bino.dra.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

// The real cost of the move to Streamable HTTP, and it sits entirely here (see ADR-0016)
public final class McpServerProcess {

    private static final Path JAR =
            Path.of("..", "mcp-payment-server", "target", "mcp-payment-server-0.1.0-SNAPSHOT.jar");
    // Fixed and not random: application.yml defaults to this port, so the fixture stays invisible
    private static final int PORT = 8081;
    private static final Duration STARTUP_MAX = Duration.ofSeconds(90);

    private static Process process;

    private McpServerProcess() {
    }

    public static synchronized void start() {
        if (process != null && process.isAlive()) {
            return;
        }
        if (!Files.isRegularFile(JAR)) {
            throw new IllegalStateException("MCP server fat jar not found at " + JAR.toAbsolutePath()
                    + "\nBuild it first: mvn -DskipTests package (from the project root).");
        }
        refuseForeignPeer();
        try {
            process = new ProcessBuilder(
                    javaBinary(), "-jar", JAR.toAbsolutePath().toString(),
                    "--server.port=" + PORT,
                    "--dra.mcp.shared-secret=" + System.getProperty("dra.mcp.shared-secret", ""))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(logFile().toFile()))
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the MCP server", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(McpServerProcess::stop));
        awaitReadiness();
    }

    public static synchronized void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    // Waits on the probe, never on a sleep: startup varies 3x between a warm box and cold CI
    private static void awaitReadiness() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest probe = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/actuator/health"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        Instant deadline = Instant.now().plus(STARTUP_MAX);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The MCP server died during startup. Log: "
                        + logFile().toAbsolutePath());
            }
            try {
                HttpResponse<String> response = client.send(probe, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (IOException expected) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the MCP server", e);
            }
            sleepBriefly();
        }
        throw new IllegalStateException("MCP server unreachable after " + STARTUP_MAX.toSeconds()
                + "s. Log: " + logFile().toAbsolutePath());
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the MCP server", e);
        }
    }

    // Compose uses this port too: left running, McpSharedSecretIT passes for the wrong reason
    private static void refuseForeignPeer() {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress("localhost", PORT), 300);
        } catch (IOException nobodyListening) {
            return;
        }
        throw new IllegalStateException("""
                Something is already listening on port %d.
                Most likely the compose stack, whose MCP server carries a DIFFERENT shared secret:
                the tests would talk to it instead of their own. Stop it first:
                    docker compose down""".formatted(PORT));
    }

    private static Path logFile() {
        return Path.of("target", "mcp-payment-server-under-test.log");
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
