package com.bino.dra.application.orchestration;

import com.bino.dra.application.port.in.DisputeJobRunner;
import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class DisputeJobService implements DisputeJobRunner {

    private static final Logger log = LoggerFactory.getLogger(DisputeJobService.class);

    private final OrchestratorService orchestrator;
    private final DisputeCaseRepository repository;
    private final Clock clock;

    public DisputeJobService(OrchestratorService orchestrator,
                             DisputeCaseRepository repository,
                             Clock clock) {
        this.orchestrator = orchestrator;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void run(Dispute dispute) {
        DisputeCase current = repository.findById(dispute.disputeId())
                .orElseGet(() -> DisputeCase.pending(dispute.disputeId(), clock.instant()));
        try {
            DisputeDecision decision = orchestrator.resolve(dispute);
            repository.save(current.done(decision, clock.instant()));
        } catch (RuntimeException failure) {
            log.error("Processing of dispute {} was interrupted", dispute.disputeId(), failure);
            repository.save(current.failed(readableCause(failure), clock.instant()));
        }
    }

    // The exception type, never its message: a message can echo an input field
    private static String readableCause(RuntimeException failure) {
        return failure.getClass().getSimpleName();
    }
}
