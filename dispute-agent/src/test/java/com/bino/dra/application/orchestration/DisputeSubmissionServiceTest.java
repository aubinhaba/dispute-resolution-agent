package com.bino.dra.application.orchestration;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.application.port.out.DisputeJobDispatcher;
import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// No Spring, no network, no key
class DisputeSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private DisputeCaseRepository repository;
    private List<Dispute> dispatches;
    private DisputeSubmissionService service;

    @BeforeEach
    void setUp() {
        repository = new CaseRepositoryDouble();
        dispatches = new ArrayList<>();
        DisputeJobDispatcher dispatcher = dispatches::add;
        service = new DisputeSubmissionService(
                repository, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void a_submitted_dispute_is_recorded_as_PENDING_and_dispatched() {
        Submission accepted = service.submit(dispute("D-1"));

        assertThat(accepted.created()).isTrue();
        assertThat(accepted.state().status()).isEqualTo(CaseStatus.PENDING);
        assertThat(accepted.state().submittedAt()).isEqualTo(NOW);
        assertThat(accepted.state().decision()).isNull();
        assertThat(dispatches).extracting(Dispute::disputeId).containsExactly("D-1");
    }

    @Test
    void a_resubmitted_dispute_is_neither_redispatched_nor_announced_as_new() {
        service.submit(dispute("D-IDEM"));
        Submission replay = service.submit(dispute("D-IDEM"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.state().disputeId()).isEqualTo("D-IDEM");
        // The list is the assertion: created=false while still dispatching would bill twice
        assertThat(dispatches).hasSize(1);
    }

    @Test
    void a_dispute_resubmitted_after_processing_returns_the_current_state() {
        service.submit(dispute("D-DONE"));
        DisputeCase finished = repository.findById("D-DONE").orElseThrow()
                .failed("SimulatedFailure", NOW);
        repository.save(finished);

        Submission replay = service.submit(dispute("D-DONE"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.state().status()).isEqualTo(CaseStatus.FAILED);
    }

    @Test
    void the_PENDING_trail_already_exists_when_the_dispatcher_is_called() {
        // Dispatching first lets the worker finish before PENDING exists, overwriting the DONE
        List<CaseStatus> seenByDispatcher = new ArrayList<>();
        DisputeSubmissionService withSpy = new DisputeSubmissionService(
                repository,
                dispute -> repository.findById(dispute.disputeId())
                        .map(DisputeCase::status)
                        .ifPresent(seenByDispatcher::add),
                Clock.fixed(NOW, ZoneOffset.UTC));

        withSpy.submit(dispute("D-2"));

        // containsExactly: a content-only assertion would pass on an empty list, proving nothing
        assertThat(seenByDispatcher).containsExactly(CaseStatus.PENDING);
    }

    @Test
    void an_unknown_dispute_returns_no_case() {
        assertThat(service.find("never-submitted")).isEmpty();
    }

    @Test
    void a_submitted_dispute_is_readable_by_its_identifier() {
        service.submit(dispute("D-3"));

        assertThat(service.find("D-3")).get()
                .extracting(DisputeCase::disputeId, DisputeCase::status)
                .containsExactly("D-3", CaseStatus.PENDING);
    }

    private static Dispute dispute(String id) {
        return new Dispute(id, "TXN-" + id, "MERCH-1", Network.VISA, "10.4",
                new Money(4_500L, "EUR"), NOW,
                NOW.plus(Duration.ofDays(30)), "Transaction not recognised.");
    }
}
