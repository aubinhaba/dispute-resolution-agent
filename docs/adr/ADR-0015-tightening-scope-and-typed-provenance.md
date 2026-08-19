# ADR-0015 — Tighten the scope to finish, and make provenance readable without a SPA

> Status: Accepted
> Date: 2026-08-10

## Context

The project was audited as a repository about to be published: every design document, the eleven
ADRs, the journals, and the 64 Java files. Three findings, all checked against the code rather than
against the intent.

**1. The remaining scope threatens completion.** After five steps and ~6,800 lines of Java, phase 1
still held: guardrails (~300 lines), the eval harness (~330), a deployable slice (~1,400), a dispute
console (~900), observability (~230). Then a reviewer agent, human-in-the-loop, case memory,
LLM-as-Judge, AWS and Terraform. The project's first discipline — finish phase 1 excellently before
chasing later phases — was not contradicted by any single decision: it was threatened by
accumulation. An unfinished portfolio whose poster promises phase 3 is **less** credible than a
smaller finished one.

**2. The project's real asset is invisible.** The separation between *what the system attests* and
*what the model proposes* is real and executable, and the project even measured and published the
half that still failed. But all of it lived in the javadoc of tests gated behind an API key. What
remained to do was not to produce more of it: it was to make it readable from the outside.

**3. An Angular SPA is the wrong vehicle for (2).** The console step planned ~900 lines, a module, an
nginx BFF, a Dockerfile and a CI job in order to encode provenance **through colour**. Colour is a
good rendering choice; the delivery cost is disproportionate. Above all, a colour in a DOM is **not
testable**. It cannot enter the eval harness, so it cannot be gated — which contradicts the project's
own arbitration rule: *we do not publish a deliverable whose measurement we cannot show*.

## Decision

**Cut four things, add one, and make provenance a property of the contract rather than a property of
the interface.**

### 1. The console step is dropped. Provenance becomes typed in the REST contract.

`GET /disputes/{id}` no longer returns a raw decision: it returns a decision in which **every element
carries its provenance label** — `ATTESTED` (derived from facts the system observed), `MODEL`
(proposed by the model, unverified), `UNTRUSTED` (third-party text). That is the decisive move: the
semantics stop being a graphical convention and become **data**.

Three consequences, and they are what justify the decision:

- it is **testable** — a test can assert that an evidence reference comes out `ATTESTED` while a
  cited rule passage comes out `MODEL`. Colour allowed none of that;
- it is **measurable** — the eval harness can count the share of attested elements, and therefore
  gate it;
- it makes rendering **trivial in any client**, including a ~150-line static HTML page served by the
  same service: no build, no nginx, no BFF, no CORS, since it is already same-origin.

The target demo keeps its coloured audit view and loses a module.

### 2. The eval harness gains a rule-passage attestation rate.

The project had found that cited rule passages do not survive the trip through the model. That debt
stops being a comment and becomes a **tracked metric**, next to decision accuracy, reason-code
accuracy and injection block rate. A known flaw that is measured beats a flaw fixed silently — and it
is the exact counterpart of point 1: typed provenance is what makes the metric computable.

### 3. A second deterministic rule: deadlines.

The dispute's raised-at and representment-due-by fields have been in the locked contract from the
start and are **read nowhere** — not by the evidence agent, not by the decision engine, not by
governance. Meanwhile the rule corpus opens on *"A large share of disputes are lost on the calendar
rather than on the facts"*: the system retrieves that rule, injects it into the prompt, and never
supplies the date.

→ The representment window enters governance as a deterministic rule, next to the amount threshold.
Expired or too close → a motivated `ESCALATE`.

This is not a comfort feature. It is the **second instance of ADR-0012**: a doctrine with a single
example is an anecdote. And it is one more eval case playable **without an API key**.

### 4. Case memory and LLM-as-Judge become "designed, not built".

They keep their number and their description — they tell well in an interview — but they are no
longer implementation targets. Case memory adds a second RAG axis that teaches little after the
compliance step; LLM-as-Judge arrives fourteenth on a project that must first finish phase 1.

### 5. The Java 25 upgrade moves to the **end** of the deployable step, never a prerequisite.

The original reasoning (`ScopedValue` to propagate the correlation id across virtual threads) stays
right. But it is the least differentiating item of the batch and it carries the only real build risk
identified — the native DJL/ONNX stack of ADR-0011. The deployable slice must run **on Java 21
first**. The upgrade is then a `pom.xml` bump plus a throughput test; if it jams, it has blocked no
deliverable.

### 6. The AWS tier is **written in Terraform, validated in CI, never kept alive**.

`terraform fmt` and `terraform validate` enter the build workflow. A single documented `apply` /
`destroy`, with the real cost observed, replaces the ambition of a living infrastructure. It is
exactly the argument already written down — IaC is the *mechanism of cost control* — pushed to its
conclusion.

## Alternatives considered

**A. Keep the console and cut elsewhere, observability for instance.** Rejected, and it was the
temptation: the console is the most *showable* part. But cutting observability to keep a front end
would mean preferring demonstration over measurement — the exact inverse of the project thesis. And
point 1 shows the demonstration is not lost: it moves from a module to a field.

**B. Keep a console, but server-side rendered.** Rejected. That saves the Node build and the BFF but
not the underlying question: the rendering would stay untestable and ungatable. The static page is
not a lesser technology choice, it is the consequence of provenance having **moved into the
contract**. Once it is there, the client can be anything — the same reasoning as ADR-0008 for the MCP
server.

**C. Handle deadlines in the prompt** ("take the due date into account"). Rejected for the reason
that already settled the amount threshold: a prompt instruction is followed *most of the time*, which
is the worst case for a rule deciding whether a case is admissible at all (ADR-0012). An expired
deadline is a fact verifiable by a subtraction, not a judgement.

**D. Cut nothing and extend the calendar.** Rejected. The cost is not time: it is that every
unfinished step keeps the showcase ahead of the code, which the audit identified as the project's
most expensive defect.

## Consequences

**Positive**

- Phase 1 becomes finishable again: it loses ~900 lines and a module, and gains ~30 (typed provenance
  plus the deadline rule).
- The project's asset becomes inspectable without compiling and without an API key — a first.
- The deterministic rule stops being an isolated case; ADR-0012 gains its second example, drawn from
  the domain rather than from infrastructure.
- The harness now measures the attestation debt instead of commenting on it.

**Negative, accepted**

- The project gives up demonstrating modern front-end skill. Trade-off: the static audit page shows
  we can render provenance; it does not show we can build a SPA. Reconsiderable if a target role
  requires it — and the typed REST contract makes that SPA easy to add later, which is the best proof
  the decision closes nothing.
- Case memory and LLM-as-Judge leave the implementation target: they must be presented as *designed*,
  never as *acquired*. Same discipline as point 4 of ADR-0013.
- Three provenance labels on a REST contract is a coarse model: a field can be partially attested.
  Accepted — a three-valued model that is tested beats a nuanced one that is never implemented.

## Relation to other decisions

- **ADR-0012** — the deadline rule is its second application, and it is what forbids putting it in
  the prompt.
- **ADR-0013** — same shape (borrow and refuse explicitly) and same engine: confront the discourse
  with the code rather than with one's idea of it.
- **ADR-0008** — typed provenance in the REST contract is, for a display client, what the standalone
  MCP server is for a tool client: the contract is the boundary, not a shared package.
- **ADR-0011** — its native stack is what justifies pushing the Java 25 upgrade to the end.
- **ADR-0014** — the total contract and the deadline rule land together: both produce a motivated
  `ESCALATE` rather than an exception.
