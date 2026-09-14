# ADR-0021 — The `gen_ai.*` metrics come from the framework, and the token budget reads the same source

> Status: Accepted
> Date: 2026-09-09

## Context

The design called for metrics named after the OpenTelemetry GenAI semantic conventions (`gen_ai.*`)
"and not after a home-grown schema", an OTLP export, and a per-dispute **token** budget that
[ADR-0013](ADR-0013-borrowings-from-the-reference-architecture.md) had deferred to this step: the
cap of 8 tool calls bounds what is *counted*, not what is *paid*.

Three facts, established by reading the jars before writing any code:

1. **Spring AI 2.0 already emits those metrics.** `AnthropicChatModel.internalCall` wraps every HTTP
   request in an `Observation`; `ChatModelMeterObservationHandler` turns it into the counter
   `gen_ai.client.token.usage` (tags `gen_ai.operation.name`, `gen_ai.system`,
   `gen_ai.request.model`, `gen_ai.response.model`, `gen_ai.token.type`). Actuator already supplies a
   `MeterRegistry`: **everything was being counted, nothing was being read.**
2. **Usage is not accumulated across the tool loop.** `call(Prompt)` passes
   `previousChatResponse = null`, so the final response carries only the last round.
3. **A `disputeId` tag would create one time series per dispute.** Spring AI itself keeps per-call
   values (`gen_ai.usage.input_tokens`) on spans, never on meters.

## Decision

- **Configure, export and prove the framework's metrics; write none of our own.**
  `micrometer-registry-otlp` plus `spring-boot-opentelemetry`, export off by default, the `metrics`
  endpoint exposed behind the API key.
- **The token cap is a `CallAdvisor` ordered after `ToolCallingAdvisor`**, so it is re-entered on
  every round. It adds up the `Usage` of each response and refuses the next round.
- **Per-dispute cap = the sum of two phase budgets** (evidence, decision), one instance per call —
  the idiom already used by `ToolCallRecorder`.
- **Degrade, never let an exception cross the port**: evidence falls back to the attested facts with
  a `+token-capped` version; the decision returns a motivated `ESCALATE` that names the budget.
- **Per-dispute correlation lives in the logs** (`disputeId`, phase, tokens), never in a tag.

## Alternatives considered

- **Write our own `dra.tokens.*` counters.** Rejected: that is precisely the home-grown schema the
  design forbids, and it would duplicate what the framework already emits.
- **Read `Usage` from the final response instead of an advisor.** One line instead of fifty.
  Rejected on measurement: on a two-round loop the final response carries 180 tokens where the
  counter sees 300. The cap would have under-counted exactly the phase that loops.
- **A counter shared between adapters, carried by the orchestrator.** It would have meant changing
  three ports to pass mutable state around. Summing phase budgets bounds the total by construction.
- **Have the cap read the global metric.** Impossible without a `disputeId` tag: the counter does not
  know which dispute a token belongs to. The cap therefore reads the same *source* — the `Usage` of
  each response — call by call.

## Consequences

- **Positive, measured without a key**: both rounds of a tool loop feed the counter (250/50) while
  the final response carries one (150); the OTLP export reaches a local collector; past the budget
  the provider receives a single request.
- **Positive, and an operational lesson**: the local ONNX embedding model emits the **same** metric
  (`gen_ai.system=onnx`). A dashboard summing `gen_ai.client.token.usage` without filtering on
  `gen_ai.operation.name` counts free tokens as billed ones. Caught red on the first run of the test.
- **Accepted downside**: the check happens *between* calls, so an overshoot is bounded by one call —
  whose output is itself bounded by `max-tokens`.
- **Accepted downside**: the `llm` reranker is outside the cap. It is not on the default path, and
  [ADR-0010](ADR-0010-modular-rag-blocks-over-advisor.md) measured that it buys nothing.
- **Not done, and written down**: the values (40 000 / 12 000 / 2 048) rest on no measurement. The
  per-phase log will produce the distribution needed to tighten them.
- **Trap worth keeping**: `OtlpMetricsExportAutoConfiguration` requires `OpenTelemetryProperties`,
  which lives in a separate Boot module. Without it the autoconfiguration is skipped **silently** —
  no bean, no error, no export. Found through the condition evaluation report (`-Ddebug=true`),
  not by deduction.
