# ADR-0023 — The predicate that classifies a dependency's failures belongs to that dependency's adapter

> Status: Accepted
> Date: 2026-09-27

## Context

`ADR-0022` set the right doctrine — *one owner per concern* — and the wiring betrayed it in a way no
test could show.

`ResilienceConfig` built **all four** beans (circuit breaker and retry, for the model and for MCP)
through two private factories whose predicates were **constants of that file**:
`ResilienceAdvisor::isProviderFailure` and `::isTransient`. Both recognise `AnthropicException` only.
An MCP outage arrives as a `ToolExecutionException`. The consequences were entirely silent:

| What `application.yml` announced | What the code did |
|---|---|
| `dra.resilience.mcp.max-attempts: 3` | **zero** retries |
| MCP breaker at 50% over 10 calls | `ignoreException` ignored **every** MCP outage, so it never opened |
| `CallNotPermittedException` → an `ESCALATE` naming MCP | **unreachable** path, therefore dead code |

Two aggravating facts, and they matter more than the bug itself:

1. **The right predicates already existed**, three files away: `RecordingToolCallback` had been
   telling `isTransportFailure` from `isBusinessError` since step 8 — but only to *translate* the
   exception, never to *configure* resilience.
2. **No test could see the defect.** `ResilienceAdvisorTest` and
   `RecordingToolCallbackResilienceTest` build their own `Retry` and `CircuitBreaker` in a fixture —
   **with the correct predicates**. They stayed green while the real beans carried the wrong ones.

## Decision

**The predicate that classifies a dependency's failures lives in that dependency's adapter, and the
resilience factories take it as a parameter.**

- `ResilienceConfig.breaker(...)` and `.retry(...)` become public and receive a
  `Predicate<Throwable>`. They no longer know about any particular dependency.
- The model's beans stay in `adapter/out/llm`, with the predicates of `ResilienceAdvisor`.
- The MCP beans move to `adapter/out/agent/McpResilienceConfig`, **next to the
  `RecordingToolCallback` whose failures they classify**.
- The `ResilienceConfig.MCP` constant disappears: `RecordingToolCallback.DEPENDENCY` is the single
  owner of that name, the one already used in escalation reasons and breaker metrics.
- **Every resilience bean is tested as production builds it**, never through an equivalent fixture.

## Alternatives considered

- **Keep all four beans in `ResilienceConfig` and inject the predicates into it.** One class fewer,
  and half the diff. Rejected: that file then remains the place where one can pick the wrong
  predicate with nothing to say so — which is exactly the mistake just fixed, made possible by
  construction. Here the physical proximity between a classification and its use is the guard rail,
  not a matter of taste.
- **A single "generic" predicate such as `failure instanceof IOException || status >= 500`.**
  Rejected for a reason that outlives this project: it cannot tell an MCP **business** error
  (unknown identifier, wrapped in an `IllegalStateException`) from a real outage. Retrying that error
  would destroy the model's self-correction loop earned in step 3, and counting it would open the
  breaker on a perfectly healthy server.

## Consequences

- **Positive**: the MCP half of `ADR-0022` finally works — 3 attempts on a transport outage, 1 on a
  business error, and the breaker counts the right ones. Measured by `McpResilienceConfigTest`, built
  on the production beans.
- **Positive**: `LlmEvidenceAgent` did not change by a single line — the bean names are the same.
- **Accepted debt**: an `adapter/out/agent` → `adapter/out/llm` dependency for the two factories.
  Tolerated by `CleanArchitectureTest`, which only constrains `domain` and `application` — but it is
  a dependency between adapters, so a choice rather than an accident. Extracting it into a neutral
  package would cost a package for two static methods.
- **What this ADR does not cover**: the two other defects found the same day (an evidence file
  without a narrative read as an `ACCEPT`, an internal `IllegalArgumentException` returned as a
  `400`). They share its shape though — **a junction between two correct mechanisms that nobody
  owned**. That is the pattern worth keeping, not the predicate.
- `ADR-0022` is **not** rewritten: its doctrine was right, its implementation was incomplete. This
  one completes it.
