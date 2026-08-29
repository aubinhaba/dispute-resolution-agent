package com.bino.dra.adapter.out.persistence;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJdbcTest
// Replace.NONE: otherwise Boot swaps in an embedded database and the test stops testing Postgres
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresTestcontainer.class, JdbcDisputeCaseRepository.class})
@TestPropertySource(properties = "dra.persistence=jdbc")
class JdbcDisputeCaseRepositoryIT extends DisputeCaseRepositoryContractTest {

    @Autowired
    private JdbcDisputeCaseRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected DisputeCaseRepository repository() {
        return repository;
    }

    @Test
    void a_transition_APPENDS_a_row_instead_of_rewriting() {
        DisputeCase pending = repository.claim(DisputeCase.pending("D-AUDIT", SUBMITTED_AT)).orElseThrow();
        repository.save(pending.done(decision("D-AUDIT"), COMPLETED_AT));

        assertThat(statusesOf("D-AUDIT")).containsExactly("PENDING", "DONE");
    }

    @Test
    void the_database_refuses_to_rewrite_a_history_row() {
        repository.claim(DisputeCase.pending("D-FROZEN-U", SUBMITTED_AT));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE dispute_case_event SET status = 'DONE' WHERE dispute_id = ?", "D-FROZEN-U"))
                .hasMessageContaining("append-only");
    }

    // Two tests, not one: Postgres aborts the transaction on the first refusal, hiding the second
    @Test
    void the_database_refuses_to_delete_a_history_row() {
        repository.claim(DisputeCase.pending("D-FROZEN-D", SUBMITTED_AT));

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM dispute_case_event WHERE dispute_id = ?", "D-FROZEN-D"))
                .hasMessageContaining("append-only");
    }

    private List<String> statusesOf(String disputeId) {
        return jdbc.queryForList(
                "SELECT status FROM dispute_case_event WHERE dispute_id = ? ORDER BY seq", String.class, disputeId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
