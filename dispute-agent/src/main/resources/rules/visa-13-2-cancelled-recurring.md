---
ruleId: visa-13.2
network: VISA
reasonCode: "13.2"
title: Cancelled Recurring Transaction
---

## Scope

This reason code applies when a merchant continued to bill a recurring
arrangement after the cardholder cancelled it, or after the issuer notified the
merchant that the account was closed or the card was blocked. It applies only to
recurring billing. A one-off purchase the cardholder no longer wants is not
covered here.

## Cancellation evidence

The cardholder must show that cancellation was communicated to the merchant
before the disputed billing date, and identify how and when. A cancellation
request sent after the billing date does not support a dispute of that billing,
only of subsequent ones.

## Merchant representment evidence

The merchant defends the dispute by showing that no valid cancellation was
received before the billing date, or that the cardholder continued to use the
service after the claimed cancellation. Evidence of the cancellation terms
accepted at sign-up, including any required notice period, is relevant where the
cardholder cancelled inside that notice period.

## Time limits

The issuer must raise the dispute within 120 calendar days of the disputed
billing date. Each recurring instalment is a separate transaction with its own
120-day window; the cardholder cannot use a recent instalment to reopen older
ones.
