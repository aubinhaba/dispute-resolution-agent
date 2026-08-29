package com.bino.dra.adapter.in.rest;

import com.bino.dra.application.port.out.DecisionEngine;
import com.bino.dra.application.port.out.EvidenceGatherer;
import com.bino.dra.application.port.out.RuleRetriever;
import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeDecision;
import com.bino.dra.domain.model.EvidenceBundle;
import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@NoDatabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.anthropic.api-key=not-used-by-this-test")
class DisputeApiIT {

    private static final int MAX_WAIT_MS = 10_000;

    @LocalServerPort
    private int port;

    @MockitoBean
    private DecisionEngine decisionEngine;

    @MockitoBean
    private EvidenceGatherer evidenceGatherer;

    @MockitoBean
    private RuleRetriever ruleRetriever;

    @Test
    void a_submitted_dispute_is_accepted_with_202_then_processed_in_the_background() {
        stubOnePath(Decision.REPRESENT);

        ResponseEntity<DisputeCaseView> accepted = submit("D-API-1");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getHeaders().getLocation()).hasToString("/disputes/D-API-1");
        assertThat(accepted.getBody()).isNotNull();
        assertThat(accepted.getBody().status()).isEqualTo(CaseStatus.PENDING);
        assertThat(accepted.getBody().disputeId().provenance()).isEqualTo(Provenance.UNTRUSTED);

        DisputeCaseView finished = awaitCompletion("D-API-1");

        assertThat(finished.status()).isEqualTo(CaseStatus.DONE);
        assertThat(finished.decision().decision().value()).isEqualTo(Decision.REPRESENT);
        assertThat(finished.decision().evidenceRefs().value()).containsExactly("TXN-EVAL-003");
        assertThat(finished.decision().citedRulePassages().value()).isNotEmpty();
        assertThat(finished.decision().evidenceRefs().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(finished.decision().citedRulePassages().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(finished.decision().rationale().provenance()).isEqualTo(Provenance.MODEL);
        assertThat(finished.completedAt()).isNotNull();
    }

    @Test
    void the_202_precedes_processing_and_carries_no_decision() {
        stubOnePath(Decision.ACCEPT);

        ResponseEntity<DisputeCaseView> accepted = submit("D-API-2");

        assertThat(accepted.getBody()).isNotNull();
        assertThat(accepted.getBody().decision()).isNull();
        assertThat(accepted.getBody().completedAt()).isNull();

        assertThat(awaitCompletion("D-API-2").decision().decision().value()).isEqualTo(Decision.ACCEPT);
    }

    @Test
    void an_identifier_never_submitted_returns_404() {
        HttpStatusCode status = rest().get().uri("/disputes/never-submitted")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void stubOnePath(Decision verdict) {
        when(evidenceGatherer.gather(any())).thenAnswer(call -> {
            Dispute d = call.getArgument(0);
            return new EvidenceBundle(d.disputeId(), d.transactionId(), "stub summary",
                    List.of("one finding"), List.of("TXN-EVAL-003"), List.of("get_transaction"),
                    false, "evidence-stub@v1", Instant.now());
        });
        when(ruleRetriever.retrieveRulePassages(any(), any()))
                .thenReturn(List.of("[visa-10.4#liability] Liability shift applies when..."));
        when(decisionEngine.decide(any(), any(), any())).thenAnswer(call -> {
            Dispute d = call.getArgument(0);
            return new DisputeDecision(d.disputeId(), verdict, 0.8, "stub rationale", "10.4",
                    List.of("[visa-10.4#liability] Liability shift applies when..."),
                    List.of("TXN-EVAL-003"), "decision-stub@v1", Instant.now());
        });
    }

    private DisputeCaseView awaitCompletion(String disputeId) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            DisputeCaseView current = rest().get().uri("/disputes/" + disputeId)
                    .retrieve().body(DisputeCaseView.class);
            if (current != null && current.status() != CaseStatus.PENDING) {
                return current;
            }
            sleepBriefly();
        }
        throw new AssertionError("Dispute " + disputeId + " still PENDING after " + MAX_WAIT_MS + " ms");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", e);
        }
    }

    private RestClient rest() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", System.getProperty("dra.security.api-key", ""))
                .build();
    }

    private ResponseEntity<DisputeCaseView> submit(String disputeId) {
        return rest().post().uri("/disputes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(disputeId))
                .retrieve()
                .toEntity(DisputeCaseView.class);
    }

    private static String body(String disputeId) {
        return """
                {
                  "disputeId": "%s",
                  "transactionId": "TXN-EVAL-003",
                  "merchantId": "MERCH-ELEC-01",
                  "network": "VISA",
                  "reasonCode": "10.4",
                  "disputedAmountMinorUnits": 4500,
                  "currency": "EUR",
                  "raisedAt": "2026-08-20T09:00:00Z",
                  "representmentDueBy": "2026-09-20T09:00:00Z",
                  "issuerClaim": "Transaction not recognised."
                }
                """.formatted(disputeId);
    }
}
