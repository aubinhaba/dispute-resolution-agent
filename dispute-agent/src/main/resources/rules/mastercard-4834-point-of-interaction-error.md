---
ruleId: mc-4834
network: MASTERCARD
reasonCode: "4834"
title: Point-of-Interaction Error
---

## Scope

This message reason code covers processing errors at the point of interaction:
the cardholder was charged an incorrect amount, charged twice for one purchase,
charged after paying by another method, or charged in a currency other than the
one agreed. The purchase itself is not disputed.

## Incorrect amount

Where the cleared amount differs from the amount the cardholder agreed, the
dispute is limited to the difference. The issuer must supply evidence of the
agreed amount, typically a receipt or an order confirmation. Where a gratuity was
added without authority, only the gratuity is disputable.

## Duplicate and alternative payment

A duplicate requires two clearings of one purchase. Payment by other means
requires evidence of the alternative settlement. In both cases the acquirer may
represent by showing two distinct purchases, or by showing that the alternative
payment covered a different obligation.

## Currency conversion

Where the cardholder was offered a conversion into their billing currency and did
not consent, or was not offered a choice at all, the conversion component is
disputable. The acquirer represents with evidence that the choice was presented
and the cardholder accepted it.

## Time limits

The issuer must submit the dispute within 120 calendar days of the central site
processing date. The acquirer has 45 calendar days to submit a second
presentment.
