---
ruleId: mc-4837
network: MASTERCARD
reasonCode: "4837"
title: No Cardholder Authorisation
---

## Scope

This message reason code applies when the cardholder states that they did not
authorise the transaction and did not participate in it. It covers both
card-present and card-absent environments, which distinguishes it from the Visa
equivalent. The cardholder must not recognise the transaction at all; a
recognised purchase with a delivery or quality complaint belongs under 4853.

## Cardholder statement

The issuer must obtain a written or electronically signed statement from the
cardholder attesting that the transaction was not authorised. The statement must
identify the specific transaction. The issuer must retain this document and
produce it if the acquirer requests it during the dispute cycle.

## Issuer prerequisites

The issuer must confirm that the account was not the subject of an existing
fraud report covering the same transaction, and must block or reissue the
account where the claim indicates account compromise. Failure to take account
action weakens the issuer position if the case proceeds to arbitration.

## Authentication and liability

Where the transaction carried a successful identity check through the network's
authentication programme, the issuer generally may not raise this dispute. Where
authentication was attempted and the issuer's system did not respond, liability
also rests with the issuer. Where no authentication was requested by the
merchant, the merchant retains liability.

## Acquirer representment evidence

The acquirer may represent by demonstrating cardholder participation. Accepted
evidence includes an authentication result, a record of the cardholder's prior
undisputed use of the same merchant account, evidence that the goods were
delivered to an address the cardholder has used before, and evidence that the
cardholder continues to use a service paid for by the disputed transaction.

## Time limits

The issuer must submit the dispute within 120 calendar days of the central site
processing date. The acquirer has 45 calendar days to submit a second
presentment. These windows differ from the Visa equivalent and are a frequent
source of procedural loss when a merchant applies one network's calendar to the
other.

## Common outcomes

Second presentment succeeds when authentication data or a documented history of
legitimate use exists. It fails when the acquirer submits only proof that an
order was placed, which does not establish who placed it.
