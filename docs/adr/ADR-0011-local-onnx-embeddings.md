# ADR-0011 — Local ONNX embeddings over a remote embeddings API

> Status: Accepted
> Date: 2026-07-28

## Context

RAG needs an embedding model to vectorise the rule corpus and the queries. **Anthropic publishes no
embeddings API**: the `spring-ai-starter-model-anthropic` starter already on the classpath provides a
`ChatModel` only. A second model source is therefore required, and the choice is structural because
it determines the reproducibility of everything measured afterwards.

Three project constraints bear on the decision:

- the first pillar is **evaluation-driven development**, with an eval gate in CI: a score that moves
  must mean something in the system changed;
- a single API key is configured (`ANTHROPIC_API_KEY`), and every additional secret is an
  operational cost and a risk;
- integration tests must run in CI, including without access to external providers.

The Spring AI 2.0 BOM offers several options: `spring-ai-starter-model-transformers` (local ONNX),
`spring-ai-starter-model-google-genai-embedding`, `spring-ai-starter-model-vertex-ai-embedding`,
`spring-ai-starter-model-postgresml-embedding`.

## Decision

Use **`spring-ai-starter-model-transformers`**: the `all-MiniLM-L6-v2` model in ONNX format, 384
dimensions, executed locally through onnxruntime. Model and tokenizer are classpath resources bundled
in the jar (`onnx/all-MiniLM-L6-v2/model.onnx`, `tokenizer.json`).

Enabled with `spring.ai.model.embedding: transformers`.

Three assumptions follow, and they are locked by a characterisation test
(`LocalEmbeddingModelTest`) rather than left implicit:

1. the dimension is 384 — a store filled with one model and queried with another raises no error, it
   simply returns nonsense;
2. the same text yields **exactly** the same vector;
3. the vector carries meaning (two texts from one domain are closer than two unrelated ones).

## Alternatives considered

**1. A remote embeddings API (Google GenAI, Vertex AI).** Better representation quality, and likely
better retrieval results. Rejected for three reasons:

- *it breaks reproducibility* — a provider updating its model shifts every vector. The eval score
  moves without a line of our code changing, and a code regression becomes indistinguishable from a
  provider update. That is precisely what the evaluation-driven pillar forbids;
- *it adds a second API key* to manage, never to commit, and to rotate;
- *it makes retrieval measurement network-dependent*. Today `RuleRetrievalIT` and
  `RerankComparisonIT` — the two tests that quantify RAG quality — run with no key and no external
  access.

**2. A local model served by Ollama.** Same determinism benefit, but requires installing and running
a third-party service on every machine and in CI. A model bundled in the jar has no such dependency.

## Consequences

**Positive.**
- Retrieval measurements are reproducible and runnable offline. That is what makes
  "precision@5: 0.40 → 1.00" a statement one can act on, and what guarantees a future deviation comes
  from our code.
- No corpus text and no query is sent to a third party for vectorisation.
- A single API key across the whole project.
- The model choice is visible in one place (`RuleVectorStoreConfig`); changing it touches no other
  class.

**Negative, accepted.**
- `all-MiniLM-L6-v2` is a small model trained mostly on English — hence the choice of an
  English-language rule corpus. Its representation quality is below that of recent commercial
  embedding models.
- Weight: roughly 150 to 200 MB of dependencies (onnxruntime, DJL tokenizers).
- **Measured caveat, not to be glossed over**: the model and tokenizer do ship inside the jar, but
  `ai.djl.huggingface:tokenizers` downloads its **native** library on first use (~259 MB into
  `~/.djl.ai`). First run observed: 69 s; subsequent runs: 1.9 s. A CI starting from a cold cache
  pays that once per image. The accurate claim is "no data sent to a third party", not "no network
  access".

**Revisit if.** The corpus grows to the point where representation quality becomes the limiting
factor, or if the move to pgvector comes with a need to share vectors with other systems. The
replacement would happen in `RuleVectorStoreConfig` alone, and `LocalEmbeddingModelTest` would be the
first test to fail — which is the point.
