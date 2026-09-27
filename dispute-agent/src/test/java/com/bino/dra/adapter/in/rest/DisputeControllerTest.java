package com.bino.dra.adapter.in.rest;

import com.bino.dra.application.orchestration.DisputeSubmissionService;
import com.bino.dra.application.orchestration.Submission;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DisputeController.class)
@Import(ApiSecurityConfig.class)
class DisputeControllerTest {

    // Relative, never a fixed date: the deadline rule turns a fixed one into a time bomb
    private static final String FAR_DEADLINE = Instant.now().plus(Duration.ofDays(60)).toString();

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisputeSubmissionService submissions;

    @Test
    void a_valid_dispute_is_accepted_with_202_and_a_Location() throws Exception {
        when(submissions.submit(any())).thenReturn(new Submission(DisputeCase.pending("D-1", NOW), true));

        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/disputes/D-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.decision").doesNotExist());
    }

    @Test
    void a_processed_dispute_returns_its_decision_and_its_audit_trails() throws Exception {
        DisputeDecision decision = new DisputeDecision("D-1", Decision.REPRESENT, 0.82,
                "Delivery evidence is consistent.", "13.1",
                List.of("[visa-13.1#proof] Proof of delivery..."), List.of("TXN-EVAL-003"),
                "decision-llm@v1.2.0", NOW);
        when(submissions.find("D-1"))
                .thenReturn(Optional.of(DisputeCase.pending("D-1", NOW).done(decision, NOW)));

        mockMvc.perform(withKey(get("/disputes/D-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.decision.decision.value").value("REPRESENT"))
                .andExpect(jsonPath("$.decision.citedRulePassages.value[0]").value("[visa-13.1#proof] Proof of delivery..."))
                .andExpect(jsonPath("$.decision.evidenceRefs.value[0]").value("TXN-EVAL-003"))
                .andExpect(jsonPath("$.decision.citedRulePassages.provenance").value("ATTESTED"))
                .andExpect(jsonPath("$.decision.evidenceRefs.provenance").value("ATTESTED"))
                .andExpect(jsonPath("$.decision.rationale.provenance").value("MODEL"))
                .andExpect(jsonPath("$.disputeId.provenance").value("UNTRUSTED"));
    }

    @Test
    void an_already_known_dispute_answers_200_and_not_202() throws Exception {
        when(submissions.submit(any()))
                .thenReturn(new Submission(DisputeCase.pending("D-1", NOW), false));

        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1")))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", "/disputes/D-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void without_an_API_key_the_controller_is_not_even_reached() throws Exception {
        mockMvc.perform(post("/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verify(submissions, never()).submit(any(Dispute.class));
    }

    @Test
    void an_unknown_dispute_returns_404_and_not_an_empty_case() throws Exception {
        when(submissions.find("never-submitted")).thenReturn(Optional.empty());

        mockMvc.perform(withKey(get("/disputes/never-submitted"))).andExpect(status().isNotFound());
    }

    @Test
    void an_unknown_network_is_refused_with_400_without_reaching_the_application() throws Exception {
        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1").replace("\"VISA\"", "\"AMEX\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("VISA or MASTERCARD")));

        verify(submissions, never()).submit(any(Dispute.class));
    }

    @Test
    void a_missing_mandatory_field_is_refused_with_400() throws Exception {
        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1").replace("\"reasonCode\": \"10.4\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("reasonCode required"));

        verify(submissions, never()).submit(any(Dispute.class));
    }

    @Test
    void malformed_JSON_is_refused_with_a_problem_detail_without_reaching_the_application() throws Exception {
        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disputeId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(submissions, never()).submit(any(Dispute.class));
    }

    @Test
    void an_unparseable_date_is_refused_with_a_problem_detail() throws Exception {
        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1").replace("\"2026-08-20T09:00:00Z\"", "\"yesterday\"")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(submissions, never()).submit(any(Dispute.class));
    }

    // Both fail before any controller method is resolved: an advice restricted to a package never sees them
    @Test
    void an_unsupported_method_answers_405_with_a_problem_detail() throws Exception {
        mockMvc.perform(withKey(put("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void an_unmapped_path_answers_404_with_a_problem_detail() throws Exception {
        mockMvc.perform(withKey(get("/nowhere")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // An IllegalArgumentException from below the controller is a server bug, not a client fault:
    // it must not come back as a 400 carrying an internal message.
    // @WebMvcTest has no error page: an unresolved exception leaves perform() instead of becoming a 500
    @Test
    void an_unexpected_IllegalArgumentException_is_not_turned_into_a_400() {
        when(submissions.submit(any())).thenThrow(new IllegalArgumentException("internal invariant"));

        assertThatThrownBy(() -> mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1"))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_Location_disputeId_is_the_case_one_and_not_the_request_one() throws Exception {
        when(submissions.submit(any())).thenReturn(new Submission(DisputeCase.pending("D-canonical", NOW), true));

        mockMvc.perform(withKey(post("/disputes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("D-1")))
                .andExpect(header().string("Location", "/disputes/D-canonical"));

        verify(submissions).submit(any(Dispute.class));
        verify(submissions, never()).find(eq("D-1"));
    }

    private static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder request) {
        return request.header("X-API-Key", System.getProperty("dra.security.api-key", ""));
    }

    private static String validBody(String disputeId) {
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
                  "representmentDueBy": "%s",
                  "issuerClaim": "Transaction not recognised."
                }
                """.formatted(disputeId, FAR_DEADLINE);
    }
}
