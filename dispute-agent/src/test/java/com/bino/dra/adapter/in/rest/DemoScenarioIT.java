package com.bino.dra.adapter.in.rest;

import com.bino.dra.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.anthropic.api-key=not-used-by-this-test")
@Import(PostgresTestcontainer.class)
class DemoScenarioIT {

    private static final String LUHN_VALID_PAN = "4111111111111111";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void a_dispute_carrying_a_PAN_produces_a_fully_attested_decision() {
        String id = "D-DEMO-IT-1";

        assertThat(submit(id, "My card " + LUHN_VALID_PAN + " was charged without my consent."))
                .isEqualTo(HttpStatus.ACCEPTED);

        DisputeCaseView view = awaitCompletion(id);

        assertThat(view.decision()).isNotNull();
        assertThat(view.decision().agentVersion().value()).startsWith("orchestrator@");
        assertThat(view.decision().decision().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.decision().rationale().provenance()).isEqualTo(Provenance.ATTESTED);
        assertThat(view.disputeId().provenance()).isEqualTo(Provenance.UNTRUSTED);
    }

    @Test
    void the_detected_number_is_echoed_nowhere() {
        String id = "D-DEMO-IT-2";
        submit(id, "Card " + LUHN_VALID_PAN + " wrongly charged.");
        DisputeCaseView view = awaitCompletion(id);

        assertThat(view.decision().rationale().value()).doesNotContain(LUHN_VALID_PAN);
        assertThat(column("SELECT coalesce(rationale, '') FROM dispute_case_event WHERE dispute_id = ?", id))
                .noneMatch(row -> row.contains(LUHN_VALID_PAN));
    }

    @Test
    void a_replay_returns_200_and_adds_no_history_row() {
        String id = "D-DEMO-IT-3";
        assertThat(submit(id, "Card " + LUHN_VALID_PAN + " disputed.")).isEqualTo(HttpStatus.ACCEPTED);
        awaitCompletion(id);

        List<String> before = column("SELECT status FROM dispute_case_event WHERE dispute_id = ? ORDER BY seq", id);
        assertThat(submit(id, "Card " + LUHN_VALID_PAN + " disputed.")).isEqualTo(HttpStatus.OK);

        assertThat(before).containsExactly("PENDING", "DONE");
        assertThat(column("SELECT status FROM dispute_case_event WHERE dispute_id = ? ORDER BY seq", id))
                .isEqualTo(before);
    }

    private List<String> column(String sql, String parameter) {
        return jdbc.queryForList(sql, String.class, parameter);
    }

    private HttpStatusCode submit(String id, String claim) {
        return rest().post().uri("/disputes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(id, claim))
                .exchange((request, response) -> response.getStatusCode());
    }

    private DisputeCaseView awaitCompletion(String id) {
        for (int attempt = 0; attempt < 100; attempt++) {
            DisputeCaseView view = rest().get().uri("/disputes/" + id).retrieve().body(DisputeCaseView.class);
            if (view != null && view.decision() != null) {
                return view;
            }
            sleepBriefly();
        }
        throw new AssertionError("Dispute " + id + " still has no decision");
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

    private static String body(String id, String claim) {
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
                  "representmentDueBy": "2026-12-20T09:00:00Z",
                  "issuerClaim": "%s"
                }
                """.formatted(id, claim);
    }
}
