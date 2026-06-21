package com.bino.dra.domain.model;

/** Outcome of an authorization consistency check (AVS or CVV). A MATCH strengthens REPRESENT. */
public enum CheckResult {
    MATCH,
    MISMATCH,
    NOT_PROVIDED
}
