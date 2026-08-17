# ADR-0014 — A validation failure becomes a motivated `ESCALATE`

> Status: Accepted
> Date: 2026-08-10

## Context

`LlmDecisionEngine` used to let an `OutputValidationException` propagate whenever the model output
failed `DraftValidator`. That was the right call at the time: a hard failure beats an invalid
output propagated in silence.

Three things changed, and together they make it untenable.

**1. The system contract says otherwise.** Every dispute must produce a `DisputeDecision`. An
exception crossing the `DecisionEngine` port is a hole in that contract: it forces every caller to
know an untyped failure mode exists, and to decide alone what to do with it.

**2. The validator became far stricter.** It now attests every `citedRulePassage` against the chunk
id of a passage that was actually retrieved. And measurement showed the model **regularly drops
that prefix** when it paraphrases the passage. The rejection rate therefore moves from "rare" to
"routine". A mechanism whose normal output was an exception becomes a mechanism whose normal output
would be a crash.

**3. A rejection is not an outage.** A malformed output is an *expected* event in a
non-deterministic system. Modelling it as an exception conflates "the model answered badly" with
"the infrastructure is down" — two things that are not handled, measured or alerted on alike.

## Decision

**An invalid output triggers a single repair round-trip; if it is still invalid, it becomes a
motivated `ESCALATE` decision.** `OutputValidationException` no longer crosses the port boundary.

In `LlmDecisionEngine.decide`:

1. call → `DecisionDraft` → `validator.validate(draft, rulePassages)`;
2. on failure, **one** further call with the original message **plus the literal violations** — the
   model has no memory between calls, so sending "fix this" alone would ask it to repair an
   analysis it can no longer see;
3. if the repair validates → normal decision, version suffixed `+repaired`;
4. if it fails → `escalateAfterFailedRepair`: `ESCALATE`, `confidence = 0.0`, `rationale` carrying
   the persistent violations, **the retrieved rule passages kept on file** (they are attested by
   the system, not by the draft that was just refused), empty `evidenceRefs`, version suffixed
   `+repair-failed`.

Two design consequences come with it, and the decision does not hold without them:

- **`DraftValidator` exempts `ESCALATE`** from the "non-empty `evidenceRefs`" and "non-empty
  `citedRulePassages`" rules. Without that exemption the failure decision would itself be invalid —
  the remedy would be rejected by the guardrail it serves.
- **Agent versions are suffixed.** `decision-llm@v1.1.0+repaired` and `…+repair-failed` make both
  populations countable without reading a log.

**Scope, not to be overstated**: this covers **validation** failures. An infrastructure failure
(network, quota, timeout) still propagates — that is a resilience concern, handled separately.
Saying "every dispute produces a decision" without that caveat would be false today.

## Alternatives considered

**Let the exception propagate and handle it in the caller (`OrchestratorService`).** Rejected: it
moves the problem without solving it. The port contract would remain "returns a decision *or*
throws", i.e. a convention rather than a type; and the next caller — an inbound REST port, an
evaluation harness — would have to catch the same exception, or forget to. A contract every caller
must remember to honour is not a contract.

**Repair in a loop, with a cap of N attempts.** Rejected for two reasons. The cost is not legibly
bounded — each attempt is a paid model call. And more importantly: a model that misses the same
explicit instruction twice will not get it on the fifth attempt; whatever success rate that buys is
paid in latency on exactly the most doubtful cases. The second attempt already has diminishing
returns, measurable by counting `+repaired` decisions.

**Overwrite `citedRulePassages` with the retrieved passages instead of rejecting.** Rejected:
technically trivial and intellectually wrong. It would yield a citation that is always valid and
never informative, because it would no longer say **what the model actually relied on** among the
passages provided. That selection is information — it is the only trace of the compliance
reasoning. Attesting means verifying a link, not manufacturing the missing one.

## Consequences

**Positive**

- The `DecisionEngine` port has a total contract: `Dispute → DisputeDecision`, with no exception to
  know about. Downstream consumers plug in without recovery code.
- A rejection becomes **measurable** rather than fatal: two version suffixes, two populations, two
  possible metrics.
- The failure decision stays **auditable**: it carries the applicable rules and the inventory of
  violations. A human analyst can see what the system attempted and why it gave up.

**Negative, accepted**

- **Cost can double** on a dispute that repairs. There is no per-dispute token cap yet: a repairing
  dispute costs two full calls, original message included.
- **`ESCALATE` now means two quite different things** — "this case deserves human eyes" and "our
  model could not answer cleanly". They are told apart only by the agent version and the rationale.
  Acceptable as long as nothing routes on the bare `ESCALATE` value; it becomes a real concern once
  a human review queue has to triage them.
- **The real failure rate is unknown at this date.** The model was observed dropping the chunk
  prefix before the guardrail existed; how often prompt v1.1.0 makes it keep the prefix is not
  measured. That is what the evaluation harness must quantify, and the reason this ADR promises no
  number.

## Link

- Builds on ADR-0004 (validated, never trusted LLM output) and ADR-0010 (the anchor lives in the
  chunk id prefix the RAG stamps on each passage).
- The `ESCALATE` exemption is what finally makes emittable the decision `OrchestratorService`
  produces on an empty evidence bundle, which until now contradicted its own validator.
