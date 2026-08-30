package com.bino.dra.adapter.out.persistence;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.CaseStatus;
import com.bino.dra.domain.model.Decision;
import com.bino.dra.domain.model.DisputeCase;
import com.bino.dra.domain.model.DisputeDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "dra.persistence", havingValue = "jdbc")
public class JdbcDisputeCaseRepository implements DisputeCaseRepository {

    private static final String[] NONE = new String[0];

    // A null seq forces an INSERT; a non-null one attempts the UPDATE the trigger refuses
    private static final Long NEW_EVENT = null;

    private final DisputeCaseEventCrud events;
    private final DisputeCaseClaimCrud claims;
    private final Clock clock;

    JdbcDisputeCaseRepository(DisputeCaseEventCrud events,
                              DisputeCaseClaimCrud claims,
                              Clock clock) {
        this.events = events;
        this.claims = claims;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<DisputeCase> claim(DisputeCase pending) {
        if (!claims.insertIfAbsent(pending.disputeId(), pending.submittedAt())) {
            return Optional.empty();
        }
        return Optional.of(save(pending));
    }

    @Override
    public DisputeCase save(DisputeCase disputeCase) {
        events.save(toRow(disputeCase));
        return disputeCase;
    }

    @Override
    public Optional<DisputeCase> findById(String disputeId) {
        return events.findFirstByDisputeIdOrderBySeqDesc(disputeId)
                .map(JdbcDisputeCaseRepository::toDomain);
    }

    private DisputeCaseEventRow toRow(DisputeCase c) {
        return c.decision() == null ? undecidedRow(c) : decidedRow(c, c.decision());
    }

    private DisputeCaseEventRow undecidedRow(DisputeCase c) {
        return new DisputeCaseEventRow(
                NEW_EVENT, c.disputeId(), c.status().name(),
                null, null, null, null, NONE, NONE, null, null,
                c.failureReason(), c.submittedAt(), c.completedAt(), clock.instant());
    }

    private DisputeCaseEventRow decidedRow(DisputeCase c, DisputeDecision d) {
        return new DisputeCaseEventRow(
                NEW_EVENT, c.disputeId(), c.status().name(),
                d.decision().name(),
                d.confidence(),
                d.rationale(),
                d.citedReasonCode(),
                d.citedRulePassages().toArray(String[]::new),
                d.evidenceRefs().toArray(String[]::new),
                d.agentVersion(),
                d.decidedAt(),
                c.failureReason(), c.submittedAt(), c.completedAt(), clock.instant());
    }

    private static DisputeCase toDomain(DisputeCaseEventRow r) {
        return new DisputeCase(
                r.disputeId(),
                CaseStatus.valueOf(r.status()),
                r.decision() == null ? null : toDecision(r),
                r.failureReason(),
                r.submittedAt(),
                r.completedAt());
    }

    private static DisputeDecision toDecision(DisputeCaseEventRow r) {
        return new DisputeDecision(
                r.disputeId(),
                Decision.valueOf(r.decision()),
                r.confidence() == null ? 0.0 : r.confidence(),
                r.rationale(),
                r.citedReasonCode(),
                List.of(r.citedRulePassages()),
                List.of(r.evidenceRefs()),
                r.agentVersion(),
                r.decidedAt());
    }
}
