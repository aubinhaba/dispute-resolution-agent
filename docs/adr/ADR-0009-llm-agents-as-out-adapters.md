# ADR-0009 — LLM agents are out adapters behind an application port

> Status: Accepted
> Date: 2026-07-26

## Context

Agents (`EvidenceAgent`, `ComplianceAgent`, `ReviewerAgent`) were initially sketched inside the
`application/agent/` layer. Intuitive enough: an agent *does* something, so it looks like a use case.

Implementing the Evidence Agent made the tension explicit. Concretely it is a Spring AI `ChatClient`
wired to MCP `ToolCallback`s — and `CleanArchitectureTest` forbids `org.springframework.ai..` inside
`..application..`. That rule has been in place since the first step and is the project's central
guard rail: *the LLM lives in an adapter, never in the business layer*.

Either the rule bends, or the agent moves. The choice is not cosmetic: it applies to all three agents
and to the orchestrator.

## Decision

**An "agent" is an implementation technique, therefore an out adapter.**

- The contract lives in `application/port/out/`: `EvidenceGatherer`, alongside `RuleRetriever` and
  `DecisionEngine`.
- The implementation lives in `adapter/out/agent/`: `LlmEvidenceAgent` and its satellites
  (`EvidenceDraft`, `ToolCallRecorder`, `RecordingToolCallback`).
- The output is a **domain** object (`EvidenceBundle`); that is what crosses the boundary, never a
  framework type.

The ArchUnit rule stays **unchanged and strict**. The test arbitrated, not the other way round.

The underlying argument: "agent" names a *technique* (an LLM looping over tools), not a *business
responsibility*. The responsibility is "obtain the evidence for a dispute" — which could tomorrow be
satisfied by a deterministic service querying a database, with no model involved. If replacing the
implementation with non-AI code leaves the contract intact, then what is being replaced is an
adapter. That is the Clean Architecture test, applied literally.

## Alternatives considered

- **Keep `application/agent/` and relax the ArchUnit rule** — rejected. The rule is the main asset
  here: it proves the domain is insulated from the LLM. Weakening it at the first friction means
  never having had a rule. It is also the opposite of the discipline applied to the eval gate
  ("never lower the threshold to make CI pass").
- **An `EvidenceAgent` use case in `application` delegating to a `ToolCallingSession` port** —
  rejected by YAGNI. That use case would carry no real deterministic policy: the model picks the
  tools, the budget belongs in the adapter (where calls are observable), and reason-code routing
  already lives in the prompt. It would add an empty indirection layer plus two interfaces to
  maintain. Worth revisiting if a genuine tool-selection policy emerges on the business side.

## Consequences

- (+) `CleanArchitectureTest` passes unmodified — evidence that the placement is right.
- (+) The orchestrator will depend on `EvidenceGatherer` and `RuleRetriever` without ever knowing an
  LLM exists, and stays testable without an API key or network.
- (+) The pattern is settled for the compliance and reviewer agents; no discussion to reopen.
- (−) A gap with multi-agent literature, which treats agents as first-class entities. Here "agent"
  names an implementation technique and the architecture names responsibilities.
- (−) One extra hop when reading the code (port ↔ implementation), mitigated by naming:
  `EvidenceGatherer` (the what) versus `LlmEvidenceAgent` (the how).
