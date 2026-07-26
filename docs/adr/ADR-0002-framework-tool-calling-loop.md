# ADR-0002 — Delegate the tool-calling loop to Spring AI rather than hand-writing it

> Status: Accepted
> Date: 2026-07-26

## Context

The Evidence Agent runs an investigation: call `get_transaction`, read the result, decide whether the
customer history or the delivery proof is needed, repeat. That is a **tool-calling loop** — the model
emits call intents, the code executes them, feeds the results back, and the cycle continues until the
model produces text.

The loop can be hand-written: drive the provider API directly, read the `toolCalls` off the response,
execute, re-inject. It has one real merit — nothing is hidden. The question is whether that
visibility is worth its maintenance cost in a system aiming at production.

Forces at play: the project already uses Spring AI 2.0 (ADR-0006) for the ChatClient and structured
output, so hand-writing the loop would mean stepping outside the framework on this one point; what
distinguishes this system is not the loop but what surrounds it (context budget, attested
traceability, guardrails); and a loop that "works on one case" is very different from a correct one.

## Decision

**The loop is delegated to Spring AI.** `LlmEvidenceAgent` makes a single Java call
(`chatClient.prompt()…toolCallbacks(…).call().entity(EvidenceDraft.class)`); the framework chains the
turns inside `ToolCallingAdvisor.adviseCall` (`do { … } while (isToolCall)`).

**What stays ours**, because it carries the project's value and the framework does not provide it:

- the **context budget** — verified in the Spring AI 2.0 source: the loop has *no* turn cap, in
  options or configuration. Enforced by `RecordingToolCallback`;
- **attested traceability** — `evidenceRefs` derived from the calls actually executed, never from the
  model's text (`ToolCallRecorder`);
- the **capability boundary** (ADR-0001) and read-only tools (enforced server-side).

The extension point used is `ToolCallback`: the ones supplied by the MCP client starter are
decorated. The model sees an identical catalogue (same names, descriptions, schemas); only execution
goes through our observer. We instrument instead of rewriting.

`RawToolLoopDemoIT` keeps a hand-written version of the same investigation as an executable
reference of the raw protocol — useful when diagnosing framework behaviour, and a check that the
delegation is understood rather than assumed.

## Alternatives considered

- **A custom loop** — rejected for production. What has to be handled: serializing tool requests in
  the provider's format, maintaining the conversation history (a chat API is stateless — forgetting
  to append the assistant message before the results breaks the loop silently), correlating by `id`
  when the model requests several tools in one turn, and turning a tool error into *feedback* rather
  than a crash. Spring AI does all of it, across providers. Writing that code means maintaining
  infrastructure instead of building what differentiates the system.
- **A general-purpose agent framework layered on top** — rejected: a second agent abstraction over
  Spring AI for a loop Spring AI already covers. Worth revisiting if orchestration needs outgrow it.

## Consequences

- (+) The agent fits in ~40 meaningful lines: the code speaks about the domain and its guarantees,
  not about JSON plumbing.
- (+) Switching model or provider does not touch the loop.
- (+) Tool errors become model feedback for free (`DefaultToolExecutionExceptionProcessor` returns
  the message as a tool result), which is what makes the server's actionable error messages
  ("do not guess or fabricate ids") pay off.
- (−) The loop is **invisible** from our code: without instrumentation we would know neither how many
  turns occurred nor what was called. That is exactly what `ToolCallRecorder` addresses.
- (−) A dependency on a framework extension point: if `ToolCallback` changes shape, the decorator
  follows. Low cost, and the BOM pins the version (ADR-0006).
- (−) One extra loop turn is one more billed model call, so the cap guards **cost** as much as context.
