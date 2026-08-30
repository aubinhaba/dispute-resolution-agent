package com.bino.dra.eval;

import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class EvalCorpusLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CURRENCY = "EUR";

    private EvalCorpusLoader() {
    }

    static List<EvalScenario> loadFunctionalCases() {
        List<EvalScenario> scenarios = new ArrayList<>();
        for (JsonNode node : read("/eval/functional-cases.json")) {
            EvalCase groundTruth = new EvalCase(
                    node.get("id").asText(),
                    Decision.valueOf(node.get("expectedDecision").asText()),
                    node.get("expectedReasonCode").asText());
            scenarios.add(new EvalScenario(
                    groundTruth,
                    dispute(node, node.get("id").asText()),
                    node.get("deterministic").asBoolean(),
                    node.get("why").asText()));
        }
        return List.copyOf(scenarios);
    }

    static List<AdversarialScenario> loadAdversarialCases() {
        List<AdversarialScenario> scenarios = new ArrayList<>();
        for (JsonNode node : read("/eval/adversarial-cases.json")) {
            String id = node.get("id").asText();
            String disputeId = node.hasNonNull("disputeIdOverride")
                    ? node.get("disputeIdOverride").asText()
                    : id;
            scenarios.add(new AdversarialScenario(
                    id,
                    dispute(node, disputeId),
                    textOrNull(node, "panField"),
                    textOrNull(node, "canary"),
                    node.get("why").asText()));
        }
        return List.copyOf(scenarios);
    }

    private static Dispute dispute(JsonNode node, String disputeId) {
        Instant now = Instant.now();
        long raisedDaysAgo = node.hasNonNull("raisedAtDaysAgo") ? node.get("raisedAtDaysAgo").asLong() : 10L;
        long dueInDays = node.hasNonNull("dueInDays") ? node.get("dueInDays").asLong() : 30L;

        return new Dispute(
                disputeId,
                node.get("transactionId").asText(),
                node.get("merchantId").asText(),
                Network.valueOf(node.get("network").asText()),
                node.get("reasonCode").asText(),
                new Money(node.get("amountMinorUnits").asLong(), CURRENCY),
                now.minus(Duration.ofDays(raisedDaysAgo)),
                now.plus(Duration.ofDays(dueInDays)),
                node.get("issuerClaim").asText());
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static JsonNode read(String resource) {
        try (InputStream stream = EvalCorpusLoader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Eval corpus not found: " + resource);
            }
            return MAPPER.readTree(stream).get("cases");
        } catch (IOException e) {
            throw new IllegalStateException("Eval corpus unreadable: " + resource, e);
        }
    }
}
