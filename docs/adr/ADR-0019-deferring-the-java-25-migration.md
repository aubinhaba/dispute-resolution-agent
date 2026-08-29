# ADR-0019 — Defer the Java 25 migration

> Status: Accepted
> Date: 2026-08-23

## Context

The stack committed to moving from Java 21 to Java 25 LTS during the deployable step, for a concrete
rather than cosmetic reason: **`ScopedValue`** (finalised in 25) is the correct way to propagate the
correlating `disputeId` across tasks running on **virtual threads**, where `ThreadLocal` does not fit
the model. ADR-0015 fixed its ordering: **last item of the last batch**, never a prerequisite,
because it is the least differentiating item and it carries the only real build risk identified —
the native DJL/ONNX stack behind the embeddings (ADR-0011).

That same paragraph planned a written fallback: *if the migration gets stuck, we ship on Java 21 and
we write it down*.

By the time the last batch opened, the deployable step had already cost **~4,200 lines against the
1,500-1,700 announced** for the whole step.

## Decision

**We do not attempt the migration.** The last batch ships security, CI and a replayable demo; the
step ends on **Java 21**.

This is **not** the fallback ADR-0015 planned, and the distinction matters: that fallback covered an
**observed technical failure**. Here nothing failed — this is a **scope decision**, taken before
trying. Saying otherwise would be flattering.

## Alternatives considered

- **Attempt it anyway.** It would probably have worked. But its only measurable benefit at the
  compose tier is a throughput test on a dispatcher that handles roughly one dispute at a time in a
  demo — we would have measured an empty pool. The real benefit of `ScopedValue` only appears under
  genuine concurrency, that is, once the system is actually deployed.
- **Move it to the observability step.** Rejected: that step carries metrics, OTLP export and a
  token cap, and has no connection to the execution model. Grafting it there would dilute two steps
  instead of finishing one.
- **Remove the commitment from the project documents.** Rejected: erasing an objective that was not
  met is exactly the flaw this project hunts — the showcase running ahead of the code, in the other
  direction.

## Consequences

**Three things that can no longer be said:**

1. **"I use virtual threads"** — no. `ExecutorDisputeJobDispatcher` stays on
   `Executors.newFixedThreadPool(4)`.
2. **"I propagate context with `ScopedValue`"** — no. Correlation happens by **explicitly passing
   the `disputeId`**, which is less elegant and perfectly sufficient for one dispute at a time.
3. **"The project is on Java 25"** — no. Java 21, and the `pom.xml` says so.

**What can be said instead, and it is worth more**: *the migration was planned, it had a precise
technical reason, and I deferred it because the step was already at three times its budget. The
reason has not gone away — it is waiting for real concurrency to be worth anything.*

The identified build risk (the native DJL/ONNX stack) remains **unresolved**: nobody yet knows
whether `ai.djl.huggingface:tokenizers` and `onnxruntime` run under Java 25. Checking that is the
first move on the day this is picked up again.

Resume target: the AWS step, where Fargate and a message queue make concurrency real — and therefore
make `ScopedValue` useful and a throughput test honest.
