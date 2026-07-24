# ADR-0008 — Standalone MCP server: no shared code with the dispute-agent

> Status: Accepted
> Date: 2026-07-16

## Context

The `mcp-payment-server` module exposes transactional data whose shape matches the domain records
(`Transaction`, `FulfillmentRecord`, `Money`) in `dispute-agent`. Should the server **reuse** those
records (a dependency on `dispute-agent`, or a shared `common` module), or define **its own** DTOs?

The DRY reflex pushes toward sharing, but MCP is a **protocol** whose whole value is to decouple the tool
provider from its consumers — which argues against sharing code.

## Decision

`mcp-payment-server` is **fully standalone**: it defines its own contractual records (`TransactionDto`,
`FulfillmentRecordDto`, `MoneyDto`, `TransactionSummaryDto`) under `com.bino.dra.mcp.model` and depends on
no other module. The only coupling between server and client is the **JSON contract** carried by the
protocol (input schemas derived from signatures, output shapes documented in the tool descriptions).

The DTOs stay **aligned with the domain vocabulary** (same field names and signal values) so the
client-side mapping is trivial — but that alignment is a convention, not a dependency. Deliberate
differences, dictated by the LLM reader: ISO-8601 `String` timestamps (not epoch), a pre-computed
`MoneyDto.formatted`, and `String` signals rather than enums (the client has no access to our classes).

## Alternatives considered

- **Shared `common` module of records** — rejected: it recreates in Maven the coupling MCP eliminates at
  the protocol level. The server would no longer be consumable/deployable without embedding project code,
  and any domain change would force a synchronized release of both sides. That is the "distributed
  monolith" anti-pattern applied to LLM tools.
- **Direct dependency `mcp-payment-server` → `dispute-agent`** — rejected, and worse: the tools server
  would drag the whole agent application classpath (Spring AI, Anthropic starter…) in for three records.

## Consequences

- (+) The server is consumable by any MCP client (Evidence Agent, Claude Desktop, Inspector) and
  deployable on its own in phase 3 — the MCP promise is kept *demonstrably*.
- (+) Each side evolves at its own pace; the JSON contract is the single synchronization point.
- (−) Accepted shape duplication: if the domain adds a field to `Transaction`, it must be considered on
  both sides. Mitigation: the eval harness (step 7) would fail on a significant misalignment, and step 3
  adds an explicit DTO→domain mapping that breaks at compile time.
- (−) Two vocabularies to keep identical (`ScaResult` enum vs `String`). The store's consistency tests
  lock the values on the server side.
