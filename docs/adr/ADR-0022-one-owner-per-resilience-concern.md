# ADR-0022 — One owner per resilience concern, and a known outage becomes a decision

> Status: Accepted
> Date: 2026-09-12

## Context

Until this step an infrastructure failure crossed the whole system: `DisputeJobService` caught it as
a last resort and marked the dispute `FAILED`.
[ADR-0014](ADR-0014-validation-failure-becomes-an-escalate.md) had written that reservation
explicitly — the total contract covered **validation** failures, not outages.

Three facts, checked in the sources before any code was written:

1. **The Anthropic SDK already retries.** `AnthropicSetup.DEFAULT_MAX_RETRIES = 2`, applied silently.
   Adding three Resilience4j attempts on top would have allowed **nine** billed calls for one
   dispute, invisible in our metrics.
2. **A timeout already exists on both sides**: 60 s on the Anthropic SDK, 30 s on the MCP client.
3. **MCP does not separate its errors.** `SyncMcpToolCallback` wraps the transport outage (cause: the
   original exception) and the `isError` business answer (cause: an `IllegalStateException` built by
   Spring AI) in the **same** `ToolExecutionException`.

## Decision

**One owner per concern**, and a known outage becomes a decision.

| Concern | Model (Anthropic) | Tools (MCP) |
|---|---|---|
| Timeout | SDK, `spring.ai.anthropic.timeout: 60s`, made explicit | `request-timeout: 30s` |
| Retry | **Resilience4j**, with `spring.ai.anthropic.max-retries: 0` | Resilience4j |
| Circuit breaker | Resilience4j, shared across disputes | Resilience4j, shared |

- **What is retried**: transient failures only — network error, 429, 5xx on the model side; transport
  outage on the MCP side. A 400 (exhausted quota) or a 401 does not heal on a second attempt.
- **What is never retried**: an MCP **business** error. It stays a message for the model, which uses
  it to correct itself — a mechanism earned in the tool-calling step that a retry would have
  destroyed silently.
- **Translation**: adapters translate their technical exceptions into `DependencyUnavailableException`,
  an **application-layer** type with no framework dependency.
- **The orchestrator decides**: a `DependencyUnavailableException` becomes a motivated `ESCALATE`
  that **names the dependency**, with `confidence = 0.0` and the `orchestrator@` version.
  **A bug keeps propagating** and still ends as `FAILED`.
- Resilience4j base modules, wired by hand: no starter targets Boot 4.
- Circuit breaker state is exposed as a metric (`resilience4j.circuitbreaker.state`).

## Alternatives considered

- **Leave the retry to the SDK and add only a circuit breaker.** Less code, but two recovery
  mechanisms, one of them invisible, and no shared policy: a 400 would still have been retried.
- **A Resilience4j `TimeLimiter` instead of the HTTP client timeout.** It requires an asynchronous
  call; ours are blocking. The timeout belongs to the client that knows how to apply it.
- **Spring Framework 7's `RetryTemplate`**, native and dependency-free. Rejected: no circuit breaker,
  so two libraries would have been needed for one concern.
- **Translate everything into `DependencyUnavailableException`, bugs included.** Tempting — no
  exception would ever cross the port — but a bug disguised as an outage produces a reassuring
  `ESCALATE` that nobody goes on to fix.

## Consequences

- **Positive, and measured without a key**: on a lasting outage the provider receives **exactly 3
  requests, not 9** — the proof that a single component owns the retry. With the breaker open the
  next call never leaves. A 400 goes out once.
- **Positive**: a dispute hit by an outage now produces an auditable decision that names the cause,
  instead of a silent `FAILED`. ADR-0014 is completed rather than contradicted: its written
  reservation said this case was still open.
- **Accepted downside**: the transport/business split on the MCP side rests on the type of the cause
  (`IllegalStateException` means business), an implementation detail of Spring AI. Two tests replay
  both branches, so a change there turns them red. It was the only **typed** discriminator available;
  the alternative was matching on a message, which breaks on the first rewording.
- **Accepted downside**: circuit breakers are per process. With several replicas, each discovers the
  outage on its own. Shared state only makes sense once the system is deployed.
- **Not done, and written down**: none of these values (3 attempts, window of 10, 50% threshold) is
  calibrated against a measurement.
