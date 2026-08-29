package com.bino.dra.adapter.out.persistence;

import org.springframework.data.repository.Repository;

import java.util.Optional;

interface DisputeCaseEventCrud extends Repository<DisputeCaseEventRow, Long> {

    DisputeCaseEventRow save(DisputeCaseEventRow row);

    Optional<DisputeCaseEventRow> findFirstByDisputeIdOrderBySeqDesc(String disputeId);
}
