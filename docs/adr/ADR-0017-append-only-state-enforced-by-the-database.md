# ADR-0017 — Append-only state and idempotency are enforced by the database, not by the code

> Status: Accepted
> Date: 2026-08-22

## Context

The first batch of the deployable step introduced the `DisputeCaseRepository` port with an in-memory
implementation. Two gaps were written down at that moment rather than discovered afterwards:

- **nothing survived the request.** An audit trail whose whole point is to outlive the process, and
  which does not outlive the process, is not one;
- **idempotency was impossible** without durable storage. A client retrying on timeout — the
  nominal case — paid for the model calls twice.

To which a property already stated on `DisputeCase` must be added: transitions produce new
instances, never mutations. What remained was to decide **who guarantees** that the trail is not
rewritten.

## Decision

**Two tables, and in both cases Postgres guarantees, not the adapter.**

- `dispute_case` carries the **claim** on an identifier. Its primary key *is* the idempotency
  mechanism: `INSERT … ON CONFLICT (dispute_id) DO NOTHING`, and zero rows inserted means "already
  known". The port gains one method, `claim(DisputeCase)`, which returns empty in that case.
- `dispute_case_event` carries the **history**, append-only. One row per transition (`PENDING` to
  `DONE` or `FAILED`); reading a dispute means reading its most recent event. A **`BEFORE UPDATE OR
  DELETE` trigger** raises.

The REST contract makes idempotency visible: **`202`** when the dispute was just accepted, **`200`**
when it was already known. Identical body and `Location` header in both cases.

Persistence uses **Spring Data JDBC**, not JPA.

## Alternatives considered

- **`findById` then `save`.** There is a race window between the two: two concurrent POSTs both get
  through, and the defect only shows under load — as two billed runs for one dispute. A uniqueness
  constraint leaves no window.
- **Append-only by adapter convention** ("we only write INSERTs"). True on the day it is written,
  false at the first refactoring: Spring Data JDBC issues an `UPDATE` by itself as soon as the key
  is non-null. A convention cannot go red; a trigger can.
- **JPA / Hibernate.** An append-only model uses neither entity lifecycle, nor lazy loading, nor the
  first-level cache. Paying for an ORM to keep only its mapping would add a layer to explain for no
  notion learned.
- **`202` on a replay**, for a more uniform contract. Rejected: the client could no longer tell
  "accepted" from "already known" without a body convention, and idempotency would become invisible
  — therefore untestable from the outside.

## Consequences

The port grows by **one** method, and it arrives with its caller — the rule that removed
`TransactionGateway` still holds.

`InMemoryDisputeCaseRepository` is **not** deleted: it becomes the fast double, and
`DisputeCaseRepositoryContractTest` replays the same assertions against both adapters. The number
that justifies keeping both: **0.07 s in memory against ~10 s on Postgres**, same assertions.

Two guarantees are only observable against a real database — hence Testcontainers rather than an H2
in compatibility mode: testing the trigger and the `ON CONFLICT` on an emulator would test the
emulator.

**Trap hit, not to be rediscovered**: the two refusals (UPDATE and DELETE) cannot be observed inside
one transaction. Postgres **aborts the whole transaction** on the first refusal, and the second
surfaces as `25P02 current transaction is aborted` — a message that no longer mentions the trigger,
and that would have led to the conclusion that DELETE goes through. Two tests, therefore two
transactions.

Accepted debt: no pagination, no purge, no retention. The table grows without bound.
