# ADR-0012 — The deterministic rule overrides the model, but does not skip it

> Status: Accepted
> Date: 2026-08-07

## Context

The orchestrator must enforce a non-negotiable business rule: above an amount threshold
(1,000.00 EUR — `dra.orchestrator.escalation-threshold-minor-units`), the dispute goes to human
review regardless of what the model recommends.

Three forces pull against each other.

1. **Governance has to be verifiable.** A rule expressed in a prompt ("if the amount exceeds
   1,000 EUR, answer ESCALATE") is a *suggestion*. The model follows it most of the time, which is
   the worst outcome available: reliable enough that people stop checking, not reliable enough to
   guarantee. It also does not survive a model upgrade — nothing says the next version reads it the
   same way.
2. **Model calls cost money.** If the outcome is known in advance above the threshold, calling the
   LLM anyway looks like waste: tokens, latency, spend.
3. **An escalation has to be usable.** The recipient of an `ESCALATE` is a human analyst. What lands
   on their desk decides whether they save time or start over.

A fourth constraint surfaced while building, and was not anticipated: `DraftValidator` rejects a
decision whose `evidenceRefs` is empty ("a decision without evidence is invalid"). On an empty
evidence bundle, calling the decision engine therefore does not produce a bad decision — it produces
an **exception**. Yet the `EvidenceGatherer` contract is explicit: an empty bundle is an *answer*,
not an error, and it is the orchestrator's job to draw the consequence.

## Decision

**The deterministic rule is enforced in Java, after the model call, and overrides its verdict.**
The orchestrator neither announces the rule to the model nor delegates its enforcement.

**Escalation is not anticipated to save the call.** On the amount path all three ports are invoked
normally; only the `decision` value is replaced with `ESCALATE`, and `rationale` is prefixed with
`[AUTOMATIC ESCALATION - <reason>]`. Everything else is preserved: the model's argument, the cited
rules, the attested evidence.

**One motivated exception: the empty bundle.** When `EvidenceBundle.isEmpty()`, the orchestrator
escalates *without* calling the decision engine and composes the `DisputeDecision` itself
(`confidence = 0.0`, `agentVersion = orchestrator@v1.0.0`, applicable rules retained).

The criterion separating the two cases is not cost. It is: **does the model have anything to reason
about?** On a large amount with a complete file, yes — and its analysis is valuable to the human who
picks up the case, even if it does not decide. On an empty file, no — it could only produce an
ungrounded opinion, which the validator rightly rejects.

## Alternatives considered

**1. Put the rule in the system prompt.** Rejected. A governance rule enforced by the component it
is meant to constrain is not a guarantee, it is a hope. The general principle: what must be
guaranteed leaves the model and becomes code; the model keeps what requires judgement. The
operational test is whether a test can fail deterministically when the rule is violated — with the
rule in a prompt, it cannot.

**2. Skip the LLM call whenever the rule applies.** Rejected on the amount path. It is the obvious
optimisation, and it produces an empty escalation: `ESCALATE`, no rationale, no cited rule, no
analysis. The analyst receives a ticket rather than a file — the system has handed over 100% of the
work after already spending its investigation budget on MCP tools and retrieval. Discarding the
synthesis at the last step wastes everything upstream rather than saving anything. Disputes above
the threshold are a minority by definition, so the saving was marginal either way.

**3. Escalate on `confidence < threshold`.** Rejected, and it is the most tempting option. An LLM's
self-reported confidence is poorly calibrated: it is not a probability, it is one more token it
generated. Routing on it hands governance back to the component being constrained — a subtler
variant of alternative 1. The field is carried for audit; no line of the orchestrator reads it.

**4. Add `budgetExhausted` as a third signal.** Deferred. It is a legitimate robust signal, but
measured across runs the tool-call ceiling is never reached (3 turns used out of 8 allowed). A
guardrail that cannot be observed firing cannot be tested honestly.

## Consequences

**Positive**

- The rule is an `if` in Java: testable without an API key, without network, deterministically. It is
  the only *value* assertion defensible anywhere in the system — everywhere else only structural
  invariants can be asserted.
- An escalated decision remains a complete file rather than an acknowledgement of receipt.
- The threshold lives in configuration and can be renegotiated without a rebuild.
- Two distinct agent versions (`decision-llm@…` / `orchestrator@…`) make it readable, in an audit
  trail, whether a model was involved at all. This matters for incident replay, and for excluding
  model-free escalations from the accuracy attributed to the model.

**Negative, accepted**

- A model call whose verdict is discarded is paid for on disputes above the threshold.
- The `[AUTOMATIC ESCALATION - …]` prefix in `rationale` is a string convention, therefore brittle.
  A consumer parsing it would break on the first wording change; if that need appears, the answer is
  a dedicated field on `DisputeDecision`, not a regex.
- Two escalation signals live in two places (`resolve` for the empty bundle, `applyGovernance` for
  the amount). It is justifiable — one asks *is there anything to judge*, the other *should the
  verdict be trusted* — but it costs readability. A third signal would warrant extracting a real
  escalation policy.

## Still open

`citedRulePassages` is **not attested**, unlike `evidenceRefs`. Measured by `OrchestratorIT`: the
compliance agent returns passages prefixed with their source chunk identifier
(`[visa-10.4#liability-shift] …`), but the model rewrites them in its own words and drops the
identifier, so the final decision carries `Fraud - Card-Absent Environment — Liability shift: …`.
The grounding exists at the retrieval boundary and does not survive the model.

The fix is not to overwrite the field with the retrieved passages — that would discard the model's
selection, which is useful information. It is to verify that each citation is traceable to a passage
actually retrieved, and repair otherwise. That is the output validator's job, and it is the next
step.

## Related

- ADR-0009 (LLM agents as out adapters) — what makes this rule testable without a key.
- ADR-0004 (validated untrusted LLM output) — the `evidenceRefs` constraint that surfaced the empty
  bundle case.
