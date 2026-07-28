# ADR-0010 — Modular RAG building blocks over RetrievalAugmentationAdvisor in the decision path

> Status: Accepted
> Date: 2026-07-28

## Context

Spring AI 2.0 ships `RetrievalAugmentationAdvisor`. In a single declaration it retrieves the relevant
documents, injects them into the prompt, calls the model, and exposes the documents it used through
the response context (`rag_document_context`). It is the obvious entry point for RAG, and the one the
reference documentation presents first.

Two constraints, both predating this step, weigh against it here.

**The port.** `RuleRetriever.retrieveRulePassages(String reasonCode, Network network)` returns a
`List<String>`: retrieval **without** generation. Generation happens elsewhere, in
`LlmDecisionEngine`, which needs the rules and the MCP evidence in the same call to reach a decision.
An advisor, by construction, sits inside a chat call and produces prose.

**Auditability.** `evidenceRefs` is attested: it is built from tool calls actually observed, never
from what the model claims (`ToolCallRecorder`). Its counterpart `citedRulePassages` must offer the
same guarantee, otherwise half of the audit trail rests on the model's word.

## Decision

The decision path uses the **building blocks** of Spring AI's modular RAG, not the advisor:

- `org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever` for metadata-filtered
  retrieval;
- `org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor` as the reranking
  extension point (`HeuristicReranker`, `LlmReranker`);
- `org.springframework.ai.rag.Query`, whose `context` map carries the reason code and network as
  exact identifiers down to the reranker.

These are exactly the blocks `RetrievalAugmentationAdvisor` assembles internally. This is not a rival
pipeline; it is the framework's own pipeline, entered one level lower.

The advisor path is **demonstrated** in a dedicated integration test, `RagAdvisorDemoIT`, outside the
decision path — the same device as `RawToolLoopDemoIT`, which showed the hand-written tool-calling
loop before the framework took it over.

## Alternatives considered

**1. `RetrievalAugmentationAdvisor` inside the decision path.** Rejected for three reasons, in order
of weight:

- *it dissolves traceability*: the advisor melts the passages INTO the prompt and returns prose. The
  documents remain readable in the response context, but nothing ties a produced sentence to a
  specific document. `citedRulePassages` must designate a real corpus chunk, not a paraphrase. A
  citation that points at nothing has no evidential value;
- *it does not return what the port asks for*: the rules would have to be extracted back out of the
  prose, one deserialisation further along than retrieval already left us;
- *it adds a model call* per dispute, in a system that already makes several.

**2. Widening the `RuleRetriever` port to return a richer object** — a compliance analysis produced
by a full LLM agent. Rejected on YAGNI and contract discipline: the port exists, its consumer
(`DecisionEngine`) already reads it, and nothing in Phase 1 needs prose in addition to the passages.
It would also be a second model call spent rewording text we already hold.

## Consequences

**Positive.**
- `citedRulePassages` is attestable: passages are re-read from the corpus and prefixed with the chunk
  identifier (`[visa-10.4#liability-shift]`). The Output Validator will be able to reject a decision
  citing a passage that was never supplied.
- Retrieval quality is measurable in isolation, with no model call: `RuleRetrievalIT` and
  `RerankComparisonIT` run without an API key. That is what makes RAG quality quantifiable in an
  offline CI, a prerequisite for the eval gate.
- One fewer model call per dispute.
- The blocks remain the framework's own: `HeuristicReranker` implements `DocumentPostProcessor` and
  plugs unchanged into a `RetrievalAugmentationAdvisor`, as `RagAdvisorDemoIT` shows.

**Negative, accepted.**
- The path is more verbose: three explicit calls (build the query, retrieve, rerank) where the
  advisor asked for one. That is the price of traceability.
- It departs from the introductory Spring AI documentation, where the advisor is the presented route.
  The departure has to be explainable — hence this ADR and `RagAdvisorDemoIT`.

**Revisit if.** A need appears to produce a natural-language answer grounded in the corpus, aimed at
a human rather than another agent — an analyst-facing explanation of a rule, for instance. The
advisor would then be the right tool, alongside the decision path rather than inside it.
