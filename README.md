# Multi-Agent Payment Dispute Resolution System

A multi-agent system that analyses payment disputes (chargebacks) and produces an **auditable**
recommendation — `REPRESENT`, `ACCEPT` or `ESCALATE` — together with its justifications (cited rules
+ mobilised evidence).

The project shows how to integrate an LLM **beyond a plain API call**: typed structured output,
output validation, a clean boundary between transactional data (MCP tools) and business rules (RAG),
and an architecture designed for evaluation.

## Why it matters

An LLM is non-deterministic. For a financial decision, "it worked in the demo" is not enough. Three
requirements shape the system:

- **Auditability** — every decision carries `citedRulePassages` and `evidenceRefs`; a decision without
  a trace is rejected.
- **Reliability** — the model output is a *draft* validated against invariants before publication; the
  system attests the metadata (`disputeId`, agent version, timestamp), the model never fabricates it.
- **Evaluability** — business logic depends on abstract ports, so accuracy is measured by an eval
  harness over a labelled set, never by asserting on a single LLM response.

## Architecture

Strict **Clean Architecture**, enforced by an ArchUnit test that prevents any framework from leaking
into the domain:

```
domain/        Pure entities (contract records) — no framework
application/   Use cases + driven ports — DecisionEngine, TransactionGateway, RuleRetriever
adapter/out/   Implementations: LLM (Spring AI), MCP client, vector store
```

Capability boundary (see `docs/adr/ADR-0001`):

| Need | Mechanism | Port |
|------|-----------|------|
| Transactional data (changes over time) | **MCP** tools | `TransactionGateway` |
| Business rules (stable corpus) | **RAG** | `RuleRetriever` |

Architecture decisions are recorded under [`docs/adr/`](docs/adr).

## Stack

- Java 21 (records, sealed types, pattern matching)
- Spring Boot 4.1 + **Spring AI 2.0** (ChatClient, structured output, advisors, MCP)
- MCP Java SDK 2.0
- JUnit 5, AssertJ, ArchUnit

## Run

Requirements: JDK 21, Maven, and an Anthropic API key.

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # read from the environment, never committed

mvn verify
```

The integration smoke test (a real model call) runs only when `ANTHROPIC_API_KEY` is set; without a
key it is disabled and the build stays green.

The `mcp-payment-server` module needs no API key: it exposes tools, it never calls a model. Its unit
tests validate the four tools directly, and it packages as a standalone STDIO server:

```bash
mvn -pl mcp-payment-server package
```

## Status

Work in progress, built in steps. The current base covers the hexagonal skeleton, the data contract,
the first structured-output LLM call and its validation guardrail, and a read-only MCP tools server
exposing four transactional tools (`get_transaction`, `get_customer_history`, `get_related_transactions`,
`get_fulfillment_record`) over mocked data via STDIO.

## License

[MIT](LICENSE).
