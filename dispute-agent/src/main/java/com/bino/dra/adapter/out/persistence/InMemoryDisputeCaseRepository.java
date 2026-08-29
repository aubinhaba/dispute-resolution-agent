package com.bino.dra.adapter.out.persistence;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.DisputeCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "dra.persistence", havingValue = "memory")
public class InMemoryDisputeCaseRepository implements DisputeCaseRepository {

    // Concurrent: the controller writes on a Tomcat thread, the worker on a dispatcher thread
    private final Map<String, DisputeCase> cases = new ConcurrentHashMap<>();

    @Override
    public Optional<DisputeCase> claim(DisputeCase pending) {
        return cases.putIfAbsent(pending.disputeId(), pending) == null
                ? Optional.of(pending)
                : Optional.empty();
    }

    @Override
    public DisputeCase save(DisputeCase disputeCase) {
        cases.put(disputeCase.disputeId(), disputeCase);
        return disputeCase;
    }

    @Override
    public Optional<DisputeCase> findById(String disputeId) {
        return Optional.ofNullable(cases.get(disputeId));
    }
}
