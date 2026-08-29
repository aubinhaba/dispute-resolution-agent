package com.bino.dra.application.orchestration;

import com.bino.dra.application.port.out.DisputeCaseRepository;
import com.bino.dra.domain.model.DisputeCase;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class CaseRepositoryDouble implements DisputeCaseRepository {

    private final Map<String, DisputeCase> cases = new HashMap<>();

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
