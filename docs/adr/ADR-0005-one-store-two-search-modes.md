# ADR-0005 — One vector store, two search modes

> Status: Accepted
> Date: 2026-08-22

## Context

This decision was originally framed as "two vector stores: pgvector for rules, OpenSearch for
cases". **That premise was revised before any implementation**: the hybrid BM25 + vector search
OpenSearch was there to justify fits inside Postgres (`tsvector` + `pgvector` + RRF fusion), and
maintaining two engines for a 90-chunk corpus was debt paid in advance.

What remains is the question the deployable step has to settle for good: is `SimpleVectorStore`
(in-memory, exhaustive search) enough, and if not, what does replacing it cost?

The real stake is not performance. Over 90 chunks, `SimpleVectorStore` compares the query against
every vector and returns an **exact** result in a fraction of a millisecond; HNSW approximates. The
stake is **checking that the `RuleRetriever` port holds** — an abstraction that has only ever had
one implementation behind it is an intention, not an architecture.

## Decision

**One store, two implementations living side by side**, selected by `dra.rag.store`
(`simple` | `pgvector`). The jar's default is `pgvector`; the `no-db` test profile selects `simple`.

`SimpleVectorStore` is not removed: it remains the store of the fast test suite, the one that runs
without Docker and without a key. The **coexistence is the demonstration** — `LlmComplianceAgent`,
`HeuristicReranker` and the `RuleRetriever` port do not change by a single line between the two.

Flyway owns the schema, with `spring.ai.vectorstore.pgvector.initialize-schema=false`: two owners
for one DDL is the surest way to stop knowing what is actually in the database.

The index is **truncated and rebuilt on every start**. Counter-intuitive for a database, and yet it
restores the property `SimpleVectorStore` offered for free: the index cannot drift from the corpus,
because it is derived from it every time.

## Alternatives considered

- **OpenSearch for hybrid search.** A second engine to deploy, back up and monitor, for a capability
  Postgres provides natively.
- **Staying on `SimpleVectorStore` and deferring pgvector to the AWS step.** Functionally
  sustainable — and that is precisely the argument against it: we would have published a system
  whose central abstraction had never been crossed by a second implementation. Both leaks below
  would have stayed invisible until AWS.
- **Reindexing only when the table is empty.** Three lines fewer, but an index that silently keeps
  rules removed from the corpus. A RAG citing a repealed rule is worse than a slow RAG.
- **A corpus fingerprint** so as to reindex only on change. ~45 lines with its test, to save ~2 s at
  startup. Unconditional truncation is shorter AND safer.

## Consequences

**What the second implementation REVEALED, and it is worth more than pgvector itself.**

1. **The audit identifier lived in a field owned by storage.** `RuleCorpusLoader` put `ruleId#section`
   into `Document.getId()`, and `LlmComplianceAgent` made it the `[chunk-id]` prefix that
   `DraftValidator` attests. `PgVectorStore` requires UUIDs: indexing blew up on
   `Invalid UUID string: shared-lifecycle#stages`. The identifier carrying the whole audit story was
   therefore changing shape depending on the store implementation.
   Fix: a `chunkId` metadata entry for audit, and `getId()` goes back to being a technical key — a
   **deterministic UUID** derived from the audit identifier. Deterministic and not random, otherwise
   reindexing at startup would invalidate every already-archived citation.
2. **An autoconfiguration does not switch off with a property of ours.**
   `DataSourceAutoConfiguration` fails before our beans exist. Two complementary mechanisms:
   `spring.autoconfigure.exclude` (the `no-db` profile) switches off the framework, while
   `dra.rag.store` / `dra.persistence` select our beans.

Accepted cost: a Postgres container for the tests in that scope (~10 s), and the obligation to write
a migration if the embedding model changes dimension. That is the right signal.

Open debt, written down: reindexing at startup **assumes a single agent**. Two replicas would
truncate each other's index. Moot at the compose tier; to be handled when the system is deployed.

**Hybrid** search (BM25 + vector + RRF) remains designed but not built. This ADR only lands the
vector half.
