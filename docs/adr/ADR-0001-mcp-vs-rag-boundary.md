# ADR-0001 — MCP (transactional data) vs RAG (business rules) boundary

> Status: Accepted
> Date: 2026-06-18

## Context

The system needs two kinds of information to settle a dispute:

1. **transactional data** that changes over time (the disputed transaction, customer history, related
   transactions, delivery proof);
2. stable **business rules** (the Visa/Mastercard reason-code sheets describing how to represent a
   given dispute).

Two LLM integration mechanisms are available: **tools** (function/tool calling, here via **MCP**) and
**RAG** (retrieving passages from a corpus, injected into the prompt). The temptation is to route
everything through one channel — e.g. exposing rules as an MCP tool `get_rule(reasonCode)`. We must
decide which channel carries what.

## Decision

We lock a clear boundary:

- **Transactional data → MCP tools**, consumed by the **Evidence Agent** (port `TransactionGateway`).
  This is live, parameterised (a `transactionId`), read-only data.
- **Business rules → RAG**, via the **Compliance Agent** (port `RuleRetriever`). This is a knowledge
  corpus retrieved by similarity then **reranked**.

Rules must **not** be exposed as an MCP tool.

## Alternatives considered

- **Expose rules as an MCP tool** (`get_rule`). Rejected: (a) a tool returns an exact value for an
  exact key, whereas a relevant rule is found by *semantic similarity* (the exact reason may differ,
  wording varies) — exactly what RAG + reranking does well; (b) mixing the two blurs the
  "data that changes" vs "stable knowledge" boundary and needlessly couples rule retrieval to the
  tool protocol.

## Consequences

- **Positive**: two LLM capabilities demonstrable independently; a clear separation reflected in the
  architecture (two ports, two agents, two out adapters). Clean auditability: `evidenceRefs` (from
  MCP) vs `citedRulePassages` (from RAG).
- **Negative / accepted debt**: two mechanisms to maintain and observe separately. If a "rule" ever
  became strongly parameterised and exact, the question should be reopened (new ADR).
- Referenced in code: ports `TransactionGateway` and `RuleRetriever` (in `application/port/out`), and
  `CleanArchitectureTest`.
