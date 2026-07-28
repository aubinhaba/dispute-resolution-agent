---
ruleId: shared-lifecycle
network: ANY
reasonCode: ANY
title: Dispute Lifecycle and Decision Options
---

## Stages

A dispute moves through a fixed sequence: the cardholder raises a claim with the
issuer, the issuer raises the dispute against the acquirer, the merchant either
accepts the loss or represents with evidence, and the issuer either accepts the
representment or escalates to pre-arbitration and arbitration. Each stage
narrows the options available.

## Representment

Representing means contesting the dispute with evidence. It is the correct choice
when the evidence directly answers the reason code raised, and when the deadline
allows a complete submission. Representing with weak evidence is not neutral: it
costs a fee, it delays resolution, and it can expose the merchant to arbitration
costs that exceed the disputed amount.

## Acceptance

Accepting means allowing the dispute to stand. It is the correct choice when the
evidence does not answer the reason code, when the liability shift places the
loss on the merchant, or when the disputed amount is smaller than the expected
cost of contesting it. Acceptance is a commercial decision, not an admission of
fault.

## Escalation to human review

Escalation is the correct choice when the case turns on a judgement the available
evidence cannot settle: conflicting records, an unusually large amount, a
regulatory dimension, or a pattern suggesting something other than an ordinary
dispute. Escalation is also correct when the evidence is thin and the deadline is
close, since a human can weigh the commercial risk.

## Evidence quality

Independent evidence outweighs merchant-controlled evidence. A carrier tracking
record, an issuer authentication result and a bank settlement reference are
independent. An internal order status, a screenshot of an admin console and a
customer service note are merchant-controlled and carry little weight on their
own, however detailed they appear.

## Fraud monitoring consequences

A dispute resolved against the merchant counts towards the merchant's fraud and
dispute ratios. Sustained ratios above the network thresholds lead to monitoring
programmes, higher fees and ultimately loss of acceptance. This is why the
cheapest immediate option is not always the correct one.
