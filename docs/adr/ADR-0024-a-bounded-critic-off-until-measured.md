# ADR-0024 — A critic bounded to one revision, that fails closed, and stays off until it is measured

> Status: Accepted
> Date: 2026-10-11

## Context

Until now the only output control was `DraftValidator`, and it is **syntactic**: field bounds, the
reason-code catalogue, a chunk-id prefix on every citation. It attests that a citation *exists* in
what was retrieved; it says nothing about what the decision *does* with it. A well-formed
`REPRESENT` whose rationale relies on a successful 3-D Secure while the evidence bundle carries
`SCA: FAILED` passes every check. That is exactly the class of the three genuine model errors of the
first eval run.

A reviewer agent closes that gap. Three forces constrain how it is built:

1. **Cost.** A critic is at least one more model call per dispute, and a critic ↔ generator loop
   has no natural bound.
2. **Independence.** Same model, same case: a critic has every reason to agree.
3. **Measurement.** A critic *changes decisions* — including correct ones — and no keyed eval run
   took place while it was built.

## Decision

**1. The flow, bounded to ONE revision.** Between "the model proposes" and governance:

```
proposed = decide()
verdict  = review(proposed)
  Coherent      → proposed                         agentVersion +reviewed
  Incoherent    → revised = revise(proposed, objections)
                  review(revised)
                    Coherent  → revised            agentVersion +revised
                    otherwise → ESCALATE           agentVersion +review-rejected
  Inconclusive  → ESCALATE                         agentVersion +review-inconclusive
```

Worst case: two decisions and two reviews. Same rule as ADR-0014 for the repair, for the same
reason: a disagreement that survives one revision is not settled by a fifth.

If it is the **revision** that does not complete — a provider outage during `revise`, or a generator
that returns `ESCALATE` itself — the suffix is `+revision-failed`: the audit names the step that
fell, and the critic, which had answered, is not blamed.

**2. Three verdicts, not two.** `ReviewVerdict` is a sealed type `Coherent` / `Incoherent` /
`Inconclusive`. An unparsable review or a provider outage is neither an agreement nor an objection.
Filing them under agreement would make the control fail **open**: the decision would leave labelled
"reviewed" without having been. Anything that is not an explicit agreement escalates. Likewise,
objections that come with `coherent: true` count as incoherence — the objections matter, not the
boolean that follows them.

**3. No review when a human will read the case anyway.** An `ESCALATE` proposed by the model, an
amount above the threshold, a missed deadline: the critic is not consulted. ADR-0012 pays for the
model call to keep its analysis on file; paying for a second opinion on a case already escalated
gives the analyst nothing more.

**4. The verdict lives in the `agentVersion` suffix.** No field added to `DisputeDecision`, no
migration, the REST contract unchanged. Suffixes now **stack** (`+repaired+revised`), so the eval
report counts them by segment rather than with `endsWith`.

**5. Off by default: `dra.reviewer.enabled: false`.** The gate floors (ADR-0003) were measured
without a critic. It is promoted like a prompt: two harness runs, off then on, comparing
`decisionAccuracy` and the `review.*` counters of the report. Those counters are **counted, not
gated** — a floor that no measurement backs would be an invented number.

**6. The critic does not see `confidence`.** It receives the case as the generator saw it, plus the
decision, the rationale and the citations. A displayed 0.93 is an argument from authority.

## Alternatives considered

- **Critic → `ESCALATE` directly, no revision.** One call, and it already yields the escalation
  signal. Rejected because it is a *filter*, not reflection. **It is the designated fallback** if
  measurement shows the revision corrects nothing — removing `revise` is then enough.
- **Iterate until agreement.** Unbounded cost, and two instances of the same model converge on
  *agreement*, not on *truth*. Agreement reached on the fourth round is attrition, not proof.
- **A critic on a different model.** The real answer to the independence problem. Left out of
  scope: a second provider to wire, a second key, a second set of outages to classify (ADR-0023).
  The `DecisionReviewer` port allows it without touching the orchestrator.
- **Critic on by default.** Rejected: a component that changes decisions is not shipped switched on
  before anyone can show what it does to the score.

## Consequences

**Positive**
- The pattern is orchestration code: `OrchestratorReviewTest` exercises all of it with no key, no
  network and no Spring context.
- Switched off, the system is the previous one: every existing test passes without an assertion
  having moved.
- Human-in-the-loop inherits a signal that is already typed (`+review-rejected`) with its reason in
  plain text.

**Negative, and accepted**
- **Nothing proves the critic improves the system.** It may degrade it: overturn a correct
  decision, or escalate what could be decided. That is the main debt, and it waits for a keyed run.
- **Reviews are NOT token-capped, and this is written down rather than dressed up.** A first
  version carried a `review-max-tokens` budget. It bounded nothing: `TokenBudgetAdvisor` refuses the
  *next* turn (ADR-0021), and a review is a single call with no tool loop — on a fresh budget it
  never refuses anything. The property and its unreachable `catch` were removed in code review.
  What is actually bounded: the decision phase, which may run twice, and the **number** of reviews
  (two). Their input size follows the size of the case.
- **Objections are a model-to-model channel.** Text generated by the critic enters the generator's
  prompt, and the `rationale` of an escalation. Mitigations: five objections at most, 300
  characters each, passed through the same delimiter neutralisation as the issuer claim (line
  breaks, brackets, quotes, `##`), and presented as DATA. The re-injected rationale gets the same
  treatment. The input guard has already rejected any PAN before the first call, so this channel
  cannot reintroduce one — but it can relay a directive injected in `issuerClaim`. The adversarial
  cases of the harness are the control; they have not been run with the critic on.
- **The review prompt version is not in the audit trail.** `+reviewed` says a review happened, not
  which `review.vX.Y.Z` did it. Acceptable while only one version exists; to fix before a second
  one is promoted.
- **Provenance**: an escalation decided by the disagreement is still labelled `MODEL`, although
  code makes the call. Same approximation as the amount-threshold escalation; not corrected here.
- `DecisionEngine.revise` is an optional operation whose default **throws**. An engine that cannot
  revise, wired behind an enabled critic, therefore fails loudly rather than returning an unrevised
  decision under a `+revised` label.
