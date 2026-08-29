package com.bino.dra.domain.model;

// Processing state, not the business verdict: a DONE case commonly carries an ESCALATE
public enum CaseStatus {

    PENDING,

    DONE,

    // Infrastructure failures only; validation failures produce a motivated ESCALATE (ADR-0014)
    FAILED
}
