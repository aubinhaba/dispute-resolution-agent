# Evaluation

How a non-deterministic system is measured here, what the numbers are worth, and what measuring
actually found. The headline figures live in the [README](../README.md); this is the methodology
behind them.

## The eval set

30 labelled cases — 20 functional plus 10 adversarial, kept **disjoint** so an attack never counts
towards accuracy — are replayed through `OrchestratorService.resolve()` and scored on four
exact-match metrics. Every run writes `target/eval-report.json`.

| Metric | Value | What it covers |
|---|---|---|
| `decisionAccuracy` | 0.85 – 0.90 | the model's verdict against the labelled ground truth |
| `reasonCodeAccuracy` | 1.00 | the cited reason code |
| `injectionBlockRate` | 1.00 | 9 attacks: 4 PANs, 5 canary injections |
| `rulePassageAttestationRate` | 1.00 | every citation carries a retrieved chunk id |

The split is the point: **what code guarantees holds at 100%, what rests on the model's judgement
holds at 85–90%.**

Two runs of the same code give 0.85 and 0.90, so the assertions are floors rather than equalities. A gate
asserting the observed value would be red half the time and would end up disabled — a disabled gate
measures nothing.

### The negative control

The most important case in the adversarial set is a 16-digit order number that is Luhn-invalid and
must *not* be rejected. Without it, a detector that refuses everything would post a perfect block
rate. A guardrail metric with no negative control measures enthusiasm, not accuracy.

## What the first run taught

The first run scored **0.55**, and six of the nine failures were not model errors: they were a
**contradiction between two specifications written by the same author**. The prompt ordered
"insufficient evidence → ESCALATE" while the corpus expected ACCEPT on empty files, and the model
obeyed the prompt.

An eval does not only measure the system — it puts specifications you believed consistent into
conflict.

The resolution, case by case:

- the prompt gave way on three of them (v1.2.0);
- three labels were genuinely wrong, and were corrected with the reason written into the case;
- the three real model errors were left alone.

Two cases still fail stably, on the boundary between "evidence absent" and "evidence inconclusive".
They are known and kept.

## What reranking is worth

The rule corpus is built so retrieval has something to get wrong: five catalogue reason codes, six
deliberately confusable sheets (Visa 10.4 and Mastercard 4837 both describe fraud), four
cross-cutting ones. Over eight labelled disputes, same corpus and queries:

| Configuration | precision@5 | Cost |
|---|---|---|
| No reranking (control) | 0.40 | none |
| Heuristic reranking | **1.00** | none |
| LLM reranking | **1.00** | one model call per dispute |

The LLM reranker buys no measurable gain here, hence the deterministic default.

### The caveat on that 1.00

Stated in `RerankComparisonIT` itself: relevance judgements are written per sheet while the reranker
ranks by sheet membership, so metric and system share a signal. That makes it a good regression
signal and weak evidence of absolute quality. A 1.00 that cannot fall for the right reasons is not a
score to be proud of; it is a tripwire.

## Running it

```bash
mvn verify                            # retrieval measurements run offline
ANTHROPIC_API_KEY=sk-ant-... mvn verify   # adds the eval harness and live-model tests
```

The reranking comparison needs no key — embeddings are local ONNX, so RAG quality is verifiable
offline and reproducibly. The 30-case harness does call the model, and is gated accordingly.
