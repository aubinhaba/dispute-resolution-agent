---
ruleId: visa-11.3
network: VISA
reasonCode: "11.3"
title: No Authorisation
---

## Scope

This reason code applies when a transaction was completed without a valid
authorisation approval, or after an authorisation was declined, or after the
authorisation had expired. It is an authorisation-process dispute. The
cardholder's identity and the delivery of goods are irrelevant here: the defect
is in how the merchant obtained approval for the funds.

## Declined and expired approvals

A transaction submitted for clearing after a decline is disputable regardless of
the merchant's reason for proceeding. An approval that has passed its validity
window is treated as absent. Repeated authorisation attempts after a decline, in
the hope of obtaining an approval, do not cure the original decline.

## Amount tolerance

Where the cleared amount exceeds the authorised amount beyond the tolerance
permitted for the merchant category, the excess is disputable. Within tolerance,
the transaction is treated as authorised. Tolerances exist for categories where
the final amount is not known at the time of authorisation, such as fuel and
hospitality.

## Merchant representment evidence

The merchant represents by supplying the authorisation code and the date it was
obtained, showing that a valid approval existed and covered the cleared amount.
Where the merchant relied on a floor limit or an offline approval permitted for
its category, it must evidence that the conditions for that exception were met.

## Time limits

The issuer must raise the dispute within 120 calendar days of the transaction
processing date. The merchant has 30 calendar days to represent.
