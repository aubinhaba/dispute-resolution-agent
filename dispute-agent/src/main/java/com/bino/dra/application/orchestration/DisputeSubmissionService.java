package com.bino.dra.application.orchestration;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.application.port.out.DisputeJobDispatcher;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

@Service
public class DisputeSubmissionService {

    private final DisputeCaseRepository repository;
    private final DisputeJobDispatcher dispatcher;
    private final Clock clock;

    public DisputeSubmissionService(DisputeCaseRepository repository,
                                    DisputeJobDispatcher dispatcher,
                                    Clock clock) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    public Submission submit(Dispute dispute) {
        Objects.requireNonNull(dispute, "dispute required");
        DisputeCase pending = DisputeCase.pending(dispute.disputeId(), clock.instant());

        Optional<DisputeCase> claimed = repository.claim(pending);
        if (claimed.isEmpty()) {
            return new Submission(knownState(dispute.disputeId()), false);
        }
        dispatcher.dispatch(dispute);
        return new Submission(claimed.get(), true);
    }

    public Optional<DisputeCase> find(String disputeId) {
        return repository.findById(disputeId);
    }

    private DisputeCase knownState(String disputeId) {
        return repository.findById(disputeId).orElseThrow(() ->
                new IllegalStateException("Dispute claimed but no state recorded: " + disputeId));
    }
}
