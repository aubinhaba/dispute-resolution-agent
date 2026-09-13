# ADR-0003 — Prompt promotion is gated by the eval, on absolute floors

> Status: Accepted
> Date: 2026-09-05

## Context

The eval gate already existed as a **mechanism**: a workflow that fails when the API secret is
missing rather than skipping silently. What it lacked was a **policy** — which floor, which prompt
version gets promoted, what to do about a regression.

Three defects, measured before writing the rule:

1. **The thresholds were literals** inside `EvalHarnessIT` (0.75 · 0.90 · 1.0 · 1.0), that is, inside
   a test gated on the API key: there was no way to check that they turn red at the right moment
   without spending ~90 model calls.
2. **`firstPassAttestationRate` was not asserted at all**, although it is the only **cost** signal in
   the set: a repair doubles the price of a case
   ([ADR-0014](ADR-0014-validation-failure-becomes-an-escalate.md)).
3. **The report never said which prompt version it measured.** Measuring v1.2.0 and then promoting
   v1.3.0 would have been a green gate on a prompt nobody evaluated.

## Decision

- The policy becomes a **pure class** (`EvalGatePolicy`), outside the key-gated test: named
  constants for the floors, a **motivated** verdict, and therefore verifiable without a key on
  fabricated reports.
- **Floors**: `decisionAccuracy` ≥ 0.75 · `reasonCodeAccuracy` ≥ 0.90 · `injectionBlockRate` = 1.0 ·
  `rulePassageAttestationRate` = 1.0 · `firstPassAttestationRate` ≥ 0.80.
- **Consistency rule**: the agent versions observed in the report must be the pinned one
  (`prompts/versions.yml`). Otherwise the report measures something other than what is promoted.
- **The report states what it measured**: observed versions, model, applied floors, verdict and
  violations.
- **Trigger**: any pull request touching `prompts/**`, plus manual runs and tags. Promoting a prompt
  means changing the pin, which means touching that directory.
- **On a regression**: fix the prompt or drop the promotion. **A floor is never lowered to make CI
  pass**; raising one requires a measurement, not an intention.

## Alternatives considered

- **Compare against the previous run (a relative baseline)** rather than absolute floors. Rejected on
  numbers: the set holds 20 functional cases, so one case is worth 0.05 — and two runs of the *same*
  code produced 0.85 and 0.90. A relative rule would flip on noise, would need two runs
  (~180–240 billed calls) and would be disabled after its first false alarm.
- **Leave the thresholds in `EvalHarnessIT`.** Zero lines to write. Rejected: a rule that cannot be
  watched turning red without a key is an intention, not a rule.
- **Gate on every push.** Rejected: ~90–120 billed calls per run would make the gate expensive
  enough to be switched off. The `prompts/**` trigger targets exactly what changes the measurement.

## Consequences

- **Positive**: the policy is testable without a key. Five cases exercise it — a drop in
  `decisionAccuracy`, a single unblocked injection, a collapsing first-pass rate, and a report that
  measures another version — plus one that passes.
- **Positive**: an eval report is now self-contained. It carries its numbers, its floors, its
  verdict, the versions measured and the model, so it stays readable months later, outside its run.
- **Accepted downside**: a prompt pull request from a fork fails for want of the secret. That is the
  price of a gate that refuses to skip silently.
- **Accepted downside**: the 0.80 floor on the first-pass rate rests on a single measurement (1.00).
  It is set as a drop detector, not as a target.
- **Not done, and written down**: no key-backed run has exercised this policy end to end yet. The
  class is proved; the wiring inside `EvalHarnessIT` will only be proved on the next real run.
