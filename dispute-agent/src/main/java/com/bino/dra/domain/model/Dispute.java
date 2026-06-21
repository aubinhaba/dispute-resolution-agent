package com.bino.dra.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Inbound dispute (chargeback) — the request the orchestrator receives.
 *
 * <p>{@code issuerClaim} is untrusted external text: it must be passed to the model as data to
 * analyse, never as an instruction (prompt-injection risk). No PAN is ever carried here.
 */
public record Dispute(
        String disputeId,
        String transactionId,
        String merchantId,
        Network network,
        String reasonCode,
        Money disputedAmount,
        Instant raisedAt,
        Instant representmentDueBy,
        String issuerClaim
) {
    public Dispute {
        Objects.requireNonNull(disputeId, "disputeId required");
        Objects.requireNonNull(transactionId, "transactionId required");
        Objects.requireNonNull(network, "network required");
        Objects.requireNonNull(reasonCode, "reasonCode required");
        Objects.requireNonNull(disputedAmount, "disputedAmount required");
    }
}
