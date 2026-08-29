# Architecture decision records

Nineteen decisions, each recorded with the alternatives that were rejected and why. The five that
carry the thesis of the project are summarised in the [README](../../README.md); this is the
complete set.

## Boundaries and layering

- [ADR-0001](ADR-0001-mcp-vs-rag-boundary.md) — MCP for transactional data, RAG for business rules
- [ADR-0002](ADR-0002-framework-tool-calling-loop.md) — Delegate the tool-calling loop to Spring AI rather than hand-write it
- [ADR-0007](ADR-0007-ports-in-application-layer.md) — Ports in the application layer, not the domain
- [ADR-0008](ADR-0008-standalone-mcp-server.md) — A standalone MCP server, sharing no code with the agent
- [ADR-0009](ADR-0009-llm-agents-as-out-adapters.md) — LLM agents are out adapters behind an application port
- [ADR-0013](ADR-0013-borrowings-from-the-reference-architecture.md) — What is borrowed from the reference agentic architecture, and what is refused

## Trusting the model no further than it can be checked

- [ADR-0004](ADR-0004-validated-untrusted-llm-output.md) — A validated LLM draft plus system-attested metadata
- [ADR-0010](ADR-0010-modular-rag-blocks-over-advisor.md) — Modular RAG blocks over `RetrievalAugmentationAdvisor` in the decision path
- [ADR-0012](ADR-0012-deterministic-rule-overrides-the-model.md) — The deterministic rule overrides the model, but does not skip it
- [ADR-0014](ADR-0014-validation-failure-becomes-an-escalate.md) — A validation failure becomes a motivated `ESCALATE`
- [ADR-0018](ADR-0018-provenance-as-a-contract-property.md) — Provenance is a property of the REST contract

## Retrieval

- [ADR-0005](ADR-0005-one-store-two-search-modes.md) — One vector store, two search modes
- [ADR-0011](ADR-0011-local-onnx-embeddings.md) — Local ONNX embeddings over a remote embeddings API

## Runtime, deployment and secrets

- [ADR-0006](ADR-0006-spring-boot-4-spring-ai-2.md) — Spring Boot 4.1 and Spring AI 2.0, not Spring Boot 3
- [ADR-0016](ADR-0016-network-mcp-transport.md) — The MCP server becomes a network service, not a child process
- [ADR-0017](ADR-0017-append-only-state-enforced-by-the-database.md) — Append-only state and idempotency enforced by the database, not the code
- [ADR-0020](ADR-0020-one-key-two-doors.md) — One key, two doors; and a separate secret for MCP

## Scope

- [ADR-0015](ADR-0015-tightening-scope-and-typed-provenance.md) — Tighten the scope to finish, and make provenance readable without a SPA
- [ADR-0019](ADR-0019-deferring-the-java-25-migration.md) — Defer the Java 25 migration
