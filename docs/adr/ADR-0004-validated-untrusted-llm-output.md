# ADR-0004 — Validated LLM draft + system-attested metadata

> Status: Accepted
> Date: 2026-06-18

## Context

An LLM can return output that is syntactically plausible but semantically wrong: out-of-range
confidence, invented reason code, decision with no evidence, fabricated metadata. In regulated
finance, propagating such output as if trustworthy is unacceptable (reliability + auditability).
We must decide the contract between "what the model produces" and "what the system publishes".

## Decision

The model never produces the final `DisputeDecision` directly. It produces a restricted
`DecisionDraft` (judgement, confidence, rationale, citations). Then:

1. **Validation** (`DraftValidator`, first pass; a full Output Validator will follow): confidence ∈
   [0,1], `citedReasonCode` in the known catalogue, non-empty `evidenceRefs`. Invalid output → fail
   hard; a single repair round-trip will be added later.
2. **System attestation**: the adapter composes the `DisputeDecision` by injecting the audit metadata
   the model is not allowed to set — `disputeId`, `agentVersion`, `decidedAt`. The model proposes, the
   system attests.

## Alternatives considered

- **Trust the raw LLM output** (map directly to `DisputeDecision`). Rejected: no invariant guarantees,
  and the model would fabricate audit metadata — an invented audit trail has no probative value.
- **Hand-parse free text** (regex). Rejected: fragile, untyped, breaks on any wording change; typed
  structured output is strictly superior.

## Consequences

- **Positive**: invariants guaranteed before any publication; clean proposal/attestation separation;
  groundwork for the repair loop and the eval harness.
- **Negative / accepted debt**: an extra DTO (`DecisionDraft`) and a composition step; the reason-code
  catalogue is currently hardcoded in the validator (to be externalised later).
- Trace: `adapter/out/llm/{DecisionDraft, DraftValidator, LlmDecisionEngine}`; deterministic tests
  `DraftValidatorTest`, `LlmDecisionEngineTest`.
