package com.bino.dra.adapter.in.rest;

import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@NoDatabase
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.anthropic.api-key=not-used-by-this-test")
class AuditPageIT {

    private static final String[] FORBIDDEN_DEPENDENCIES =
            {"http://", "https://", "src=", "href=", "@import", "//cdn", "//fonts"};

    @LocalServerPort
    private int port;

    @Test
    void the_audit_page_is_served_and_calls_no_external_host() {
        String page = RestClient.create("http://localhost:" + port)
                .get().uri("/audit.html").retrieve().body(String.class);

        assertThat(page).isNotNull();
        assertThat(page).contains("Dispute audit trail");
        assertThat(page).contains("ATTESTED", "MODEL", "UNTRUSTED");
        assertThat(page)
                .as("the audit page must be self-contained: no CDN, no remote font")
                .doesNotContain(FORBIDDEN_DEPENDENCIES);
    }

    @Test
    void the_page_never_injects_data_as_HTML() {
        String page = RestClient.create("http://localhost:" + port)
                .get().uri("/audit.html").retrieve().body(String.class);

        // Targets the CALLS, not the word: matching the bare word went red on the comment forbidding it
        assertThat(page).doesNotContain(
                "innerHTML=", "innerHTML =", "outerHTML=", "outerHTML =",
                "insertAdjacentHTML(", "document.write(");
        assertThat(page).contains("textContent");
    }
}
