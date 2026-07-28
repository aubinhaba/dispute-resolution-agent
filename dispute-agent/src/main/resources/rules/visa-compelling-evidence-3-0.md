---
ruleId: visa-ce3.0
network: VISA
reasonCode: ANY
appliesTo: 10.4
title: Compelling Evidence 3.0 for Card-Absent Fraud Disputes
---

## Purpose

Compelling Evidence 3.0 is a defence available to merchants against card-absent
fraud disputes. It allows a merchant to demonstrate a prior relationship with
the cardholder rather than to prove the disputed transaction directly. It applies
to fraud reason codes only; it is not available against non-receipt or
not-as-described disputes.

## Qualifying prior transactions

The merchant must identify at least two prior undisputed transactions with the
same cardholder, each more than 120 calendar days and less than 365 calendar days
before the disputed transaction. Transactions that were themselves disputed, or
refunded, do not qualify, and neither do transactions outside that window.

## Required matching data elements

Each qualifying prior transaction must share at least two identifiers with the
disputed transaction: the device fingerprint, the customer account identifier on
the merchant's platform, the delivery address, or the IP address. Matching the
card number alone is not sufficient, because a compromised card number would
match by definition.

## Effect when accepted

Where the evidence qualifies, liability moves to the issuer and the dispute is
resolved in the merchant's favour without arbitration. The issuer may not
re-dispute on the same reason code. A qualifying submission also prevents the
transaction from counting against the merchant's fraud monitoring ratio.

## Effect when rejected

Where the submission does not meet the data requirements, it is rejected on
procedure and the merchant loses the opportunity to represent on other grounds
for that dispute. Submitting a partial data set is therefore worse than
submitting a conventional representment.

## Relationship to authentication

Compelling evidence is a fallback for transactions that were not authenticated.
Where the transaction carried a successful authentication, the liability shift
already protects the merchant and this defence is unnecessary.
