# ADR-0018 — Provenance is a property of the REST contract

> Status: Accepted
> Date: 2026-08-22

## Context

ADR-0015 dropped the dispute-console step on a precise argument: *a colour in a DOM is neither
testable nor gatable*, so it contradicts the project's arbitration rule. Provenance had to become a
property of the **contract**. What remained was to decide in what shape, and under what rule.

The underlying problem belongs to every AI output: it presents what is guaranteed and what is
merely plausible **on the same footing**. In the first batch, `GET /disputes/{id}` returned the raw
`DisputeDecision`. Nothing let a reader guess that `evidenceRefs` is rewritten by the orchestrator
from the observed tool trail while `rationale` is model prose.

## Decision

Three labels — `ATTESTED`, `MODEL`, `UNTRUSTED` — carried **by the value itself**:
`{"value": …, "provenance": "ATTESTED"}`.

**The labelling rule fits in one question: did a model intervene?** It is read from `agentVersion`,
whose `orchestrator@` prefix was introduced precisely so that this can be stated rather than
guessed. If yes, `decision`, `confidence`, `rationale` and `citedReasonCode` are `MODEL`; if no —
amount threshold, deadline rule, empty bundle, PAN guard rejection (ADR-0012) — they are `ATTESTED`.

`citedRulePassages` and `evidenceRefs` are **always** `ATTESTED`, whoever the author is.
`disputeId` is **always** `UNTRUSTED`: it is the only field returned exactly as the caller supplied
it, and one of the four that `PromptSafetyGuard` scans.

Lifecycle fields (`status`, `submittedAt`, `completedAt`, `failureReason`) are **not** labelled:
they never leave the deterministic path, and marking them would dilute the signal.

Rendering by `static/audit.html`: no build, no framework, no external request, served same-origin.
It decides nothing — it colours according to the label the API returned.

## Alternatives considered

- **A parallel map**, `{"provenances": {"rationale": "MODEL"}}`. Lighter to serialise, but it can
  drift from the fields it describes: a field added without its entry would go unnoticed. Carried by
  the value, the label cannot be missing.
- **Labelling every field**, lifecycle included. A more uniform contract, a weaker signal: if
  everything is labelled, the label stops pointing at the place where caution is required.
- **`Provenance` in the domain** rather than in `adapter/in/rest`. Rejected: it answers "how do we
  render this to a third party", not a business rule about disputes. The domain does not know its
  readers.
- **A single-page application.** ~900 lines, a module, an nginx BFF, a CI job — for information no
  test could have verified.

## Consequences

**Labelling is gatable**, and that is the whole point: `DisputeCaseViewTest` and
`DisputeControllerTest` make it go red. A regression breaks an assertion, not merely a colour.

`ATTESTED` covers **two different mechanisms**, and the difference is the interesting half:
`evidenceRefs` is *produced* by the system (rewritten from the tool trail), `citedRulePassages` is
only *checked* (each citation must carry the prefix of a genuinely retrieved passage). We do not
overwrite the latter with the five RAG passages: the selection belongs to the model, and that is
information.

`DisputeCaseResponse` is removed. The REST contract changes shape — moot here, since no client
exists outside the repository, but worth noting as a break should one ever exist.

The page displays a field explicitly declared untrusted: **`textContent` only**, never HTML
injection. `AuditPageIT` bans `src`, `href`, `@import` and `http` **wholesale** rather than by host
list — a self-contained audit page has no reason to load anything, and a remote font would leak the
identifier of the dispute being viewed in a third party's `Referer`.

Read-only for now: the page has no write action.
