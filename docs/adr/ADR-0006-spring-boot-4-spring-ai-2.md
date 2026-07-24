# ADR-0006 — Spring Boot 4.1 + Spring AI 2.0 (not Spring Boot 3)

> Status: Accepted
> Date: 2026-06-18

## Context

The initial framing pinned "**Spring Boot 3** + **Spring AI 2.0** + **MCP Java SDK 2.0**". Checking the
real versions against official sources (2026-06-18) revealed an **incompatibility**: Spring AI 2.0.0 is
built on Spring Framework 7 / Spring Boot 4.x / Jakarta EE 11 and is **not** backward-compatible with
Boot 3.x — so "Spring Boot 3 + Spring AI 2.0" is a non-existent combination. Yet the APIs the project
relies on (`@McpTool`, `ChatClient`, RAG advisors, structured output) are Spring AI 2.0.

## Decision

Align the stack on **Spring Boot 4.1.0** (GA 2026-06-10) + **Spring AI 2.0.0** (GA 2026-06-12) +
**MCP Java SDK 2.0.0**, on **Java 21**. The "Spring Boot 3" mentions from the initial framing are
corrected to "Spring Boot 4.1". Versions are pinned once, in the parent POM BOM
(`<spring-boot.version>`, `<spring-ai.version>`).

## Alternatives considered

- **Stay on Spring Boot 3 and downgrade Spring AI to 1.x.** Rejected: it contradicts the explicit
  "Spring AI 2.0" / "MCP SDK 2.0" targets, and the `@McpTool` API / advisor model differ between 1.x
  and 2.0 — that would mean learning an API on its way to obsolescence for a project whose goal is
  mastering current tooling.

## Consequences

- **Positive**: a coherent, up-to-date stack (GA only days old). Java 21 is compatible with Boot 4 /
  Framework 7.
- **Negative / accepted debt**: Spring Boot 4 / Framework 7 are recent — risk of sparse third-party
  docs and some API breaks versus Boot 3. Watch on each minor version bump.
- Trace: `pom.xml` (version-choice comment).
