# Eval set — first batch (specification, not executable)

> **Status: specification (eval-as-spec).** These cases are not executed yet (the harness and the
> mocked `Dispute`/`Transaction` data do not exist yet). They are written early to fix the target
> before building (eval-driven) and to settle two structural decisions (escalation threshold +
> reason-code catalogue). Each `disputeId` will become real data and an executable `EvalCase` once
> the mocked store is in place.

## Decisions settled by these cases

### Escalation threshold (deterministic rule)
**`disputedAmount` > 1000.00 € (i.e. `minorUnits > 100000`) → `ESCALATE`**, whatever the reason.
Rationale: above some amount, the financial risk of a wrong automated decision outweighs the cost of
human review. Deterministic rule (no LLM): it must be trivial to audit and test. Exact value to be
confirmed with the business; 1000 € is a starting point.

### Known reason-code catalogue (for the Output Validator)
A decision whose `citedReasonCode` is NOT in this catalogue is rejected.

| Code | Network | Meaning (simplified) |
|------|---------|----------------------|
| 10.4 | Visa | Fraud — card-absent environment |
| 13.1 | Visa | Goods / service not received |
| 13.3 | Visa | Not as described / defective |
| 4837 | Mastercard | Unauthorized transaction (fraud) |
| 4855 | Mastercard | Goods / service not provided |

## Evaluation cases

| disputeId | reasonCode | Key context | expectedDecision | Why |
|-----------|-----------|-------------|------------------|-----|
| `EVAL-001` | 10.4 (Visa) | Fraud, 3DS `NOT_AUTHENTICATED`, AVS `MISMATCH`, 45.00 € | `ACCEPT` | No liability shift (3DS failed) + weak signals → fighting is a lost cause. |
| `EVAL-002` | 10.4 (Visa) | Alleged fraud but 3DS `AUTHENTICATED`, AVS `MATCH`, CVV `MATCH`, 120.00 € | `REPRESENT` | Liability shifted to the issuer + strong signals → fight with evidence. |
| `EVAL-003` | 13.1 (Visa) | Goods not received, `FulfillmentRecord`: shipped + tracking + delivered, 80.00 € | `REPRESENT` | Delivery proof is the decisive argument against a "not received" reason. |
| `EVAL-004` | 13.3 (Visa) | Not as described, amount 1,500.00 € (> 1000 € threshold) | `ESCALATE` | Deterministic amount rule: takes precedence over any LLM analysis. |

## What these 4 cases cover

- **All 3 decisions**: `ACCEPT` (001), `REPRESENT` (002, 003), `ESCALATE` (004).
- **Two distinct paths to `REPRESENT`**: 3DS liability shift (002) vs delivery proof (003) — to check
  the agent does not reason on a single signal.
- **Precedence of the deterministic rule** (004) over the model's judgement.
- **The non-determinism pitfall**: we score by exact-match over a set, never by asserting on a single
  (fragile) LLM response.
