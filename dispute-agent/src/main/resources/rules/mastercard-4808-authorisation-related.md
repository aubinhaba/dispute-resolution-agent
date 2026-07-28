---
ruleId: mc-4808
network: MASTERCARD
reasonCode: "4808"
title: Authorisation-Related Chargeback
---

## Scope

This message reason code covers transactions cleared without a required
authorisation, cleared after a decline, cleared on an expired approval, or
cleared on an account that was already listed as blocked at the time of the
transaction. It is a processing dispute; the cardholder's identity and the
delivery of goods are not at issue.

## Required authorisation

An authorisation is required for every transaction above the merchant's floor
limit, and for every card-absent transaction regardless of amount. A merchant
that clears without approval carries the loss even where the cardholder does not
dispute the purchase itself.

## Blocked accounts

Where the account appeared on the network's blocked list before the transaction
date, and the merchant did not check, the transaction is disputable. The
acquirer may represent by showing that the check was performed and returned no
match at the time.

## Acquirer representment evidence

The acquirer represents with the authorisation identification response and its
date, or with evidence that the transaction fell within an exception permitted
for the merchant category. Partial approvals must be evidenced with the approved
amount, and any excess remains disputable.

## Time limits

The issuer must submit the dispute within 90 calendar days of the central site
processing date, which is shorter than the window for cardholder-driven
disputes. The acquirer has 45 calendar days to submit a second presentment.
