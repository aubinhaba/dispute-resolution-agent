package com.bino.dra.application.port.out;

import com.bino.dra.domain.model.DisputeCase;

import java.util.Optional;

public interface DisputeCaseRepository {

    Optional<DisputeCase> claim(DisputeCase pending);

    DisputeCase save(DisputeCase disputeCase);

    Optional<DisputeCase> findById(String disputeId);
}
