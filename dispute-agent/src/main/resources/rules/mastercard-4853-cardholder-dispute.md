---
ruleId: mc-4853
network: MASTERCARD
reasonCode: "4853"
title: Cardholder Dispute - Defective or Not as Described
---

## Scope

This message reason code covers the cardholder's complaint that goods or
services received were defective, damaged, or materially different from the
description at the point of sale. The cardholder acknowledges the purchase and
acknowledges receipt. Non-receipt belongs under 4855, and non-participation
belongs under 4837.

## Attempted resolution

The issuer must confirm that the cardholder attempted to resolve the matter with
the merchant, or that such an attempt was not possible. The date and method of
the attempt must be recorded. This prerequisite is enforced more strictly here
than under the non-receipt codes, because the merchant is entitled to an
opportunity to repair or replace.

## Return of goods

Where physical goods are involved and remain with the cardholder, the goods must
have been returned or offered for return. The acquirer may represent by showing
that no return was attempted, or that the returned item was not the item
originally supplied.

## Acquirer representment evidence

The acquirer represents with the original description or specification supplied
to the cardholder, evidence that the delivered item conformed to it, records of a
repair or replacement already provided, or evidence that the cardholder retained
and continued to use the goods.

## Time limits

The issuer must submit the dispute within 120 calendar days of the delivery date
or the date the defect became apparent, subject to a maximum of 540 days from the
transaction. The acquirer has 45 calendar days to submit a second presentment.
