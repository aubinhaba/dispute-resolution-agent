package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.DisputeCase;

import java.util.Optional;

public interface DisputeCaseRepository {

    // Atomic by construction, not findById-then-save: that window bills one dispute twice
    Optional<DisputeCase> claim(DisputeCase pending);

    DisputeCase save(DisputeCase disputeCase);

    Optional<DisputeCase> findById(String disputeId);
}
