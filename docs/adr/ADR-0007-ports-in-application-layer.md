# ADR-0007 — Ports in the application layer (Clean Architecture) rather than the domain

> Status: Accepted
> Date: 2026-06-18

## Context

Initially, the out ports (`TransactionGateway`, `RuleRetriever`, `DecisionEngine`) were placed in
`domain/port`, following the hexagonal "DDD" school where the domain owns the interfaces it needs.
The initial project framing reflected that choice.

Two schools coexist for the LOCATION of ports, both respecting dependency inversion:

- **Hexagonal (DDD)**: the domain defines its ports → `domain/port`.
- **Clean Architecture (R. C. Martin)**: *Entities* (innermost layer) are pure enterprise rules;
  ports are *Use Case* boundaries and therefore live in the application layer. An entity does not even
  know the ports.

## Decision

We adopt **Clean Architecture**: ports live in the **application** layer.

- `domain/model`: pure entities (contract records), no port, no framework.
- `application/port/out`: driven ports (`TransactionGateway`, `RuleRetriever`, `DecisionEngine`).
- `application/port/in`: inbound ports (exposed use cases) — to come.

The dependency rule (inner ← outer) is enforced by `CleanArchitectureTest`: the domain depends on
neither application, adapters, nor a framework; the application depends on neither adapters nor
Spring AI.

## Alternatives considered

- **Keep ports in `domain/port` (hexagonal DDD).** Rejected: our ports are clearly USE-CASE
  collaborators, not entity invariants — the `Dispute` entity has no need of a `TransactionGateway`.
  Placing them next to their consumer (the application layer) is more cohesive and keeps the domain
  reusable across use cases. (A preference, not a bug fix: the hexagonal option would have worked.)

## Consequences

- **Positive**: cleaner Entities / Use Cases separation; strictly pure domain; the architecture
  guardrail expresses the layer dependency rule explicitly.
- **Negative / accepted debt**: one extra package level (`application/port/out`); a deviation from the
  initial framing (ports in the domain) — corrected here.
- Supersedes the initial layout (ports in the domain); supersedes no other ADR.
- Trace: `CleanArchitectureTest`, ADR-0001 (MCP/RAG boundary, unchanged).
