package com.bino.dra.adapter.out.persistence;

import com.bino.dra.application.port.out.DisputeCaseRepository;

class InMemoryDisputeCaseRepositoryTest extends DisputeCaseRepositoryContractTest {

    private final DisputeCaseRepository repository = new InMemoryDisputeCaseRepository();

    @Override
    protected DisputeCaseRepository repository() {
        return repository;
    }
}
