---
ruleId: visa-10.4
network: VISA
reasonCode: "10.4"
title: Fraud - Card-Absent Environment
---

## Scope

This reason code applies to card-absent transactions — e-commerce, mail order and
telephone order — where the cardholder states that neither they nor anyone
authorised by them took part in the transaction. It does not apply to
face-to-face transactions, and it does not apply where the cardholder recognises
the purchase but disputes what was delivered. A cardholder who received nothing
but does recognise the merchant belongs under 13.1, not here.

## Cardholder claim

The issuer must hold a statement from the cardholder that the transaction was
not authorised by them. The statement must be specific to the transaction under
dispute. A general assertion that the card was compromised at some point is not
sufficient to support an individual dispute, because it does not establish that
this particular transaction was fraudulent.

## Issuer requirements

Before raising the dispute the issuer must confirm that the account was not
subject to a valid authentication that shifted liability, and that the
transaction was not previously charged back for the same reason. The issuer must
also confirm that the card was not reported lost or stolen after the transaction
date in a way that contradicts the cardholder statement.

## Liability shift

Where the transaction was successfully authenticated through 3-D Secure, the
fraud liability moves to the issuer and this dispute is not available. Where
authentication was attempted but the issuer's directory server was unavailable,
liability also moves to the issuer. Where authentication was not applied at all,
liability stays with the merchant and the dispute may proceed.

## Merchant representment evidence

The merchant may defend the dispute by showing that the cardholder participated
in the transaction or benefited from it. Evidence that carries weight includes a
successful 3-D Secure authentication result, a record of previous undisputed
transactions from the same device and account, delivery to an address previously
used by the cardholder, and a link between the disputed purchase and an ongoing
service the cardholder still uses.

## Weak or irrelevant evidence

An address verification match on its own does not defend this dispute, because
billing address data is commonly available to a fraudster. A card verification
value match on its own is likewise insufficient. Proof that the merchant's
website displayed its terms and conditions is not relevant to whether the
cardholder authorised the transaction.

## Time limits

The issuer must raise the dispute within 120 calendar days of the transaction
processing date. The merchant has 30 calendar days from the dispute date to
submit representment evidence. Late representment is rejected on procedure and
the funds stay with the issuer regardless of the strength of the evidence.

## Common outcomes

Representment succeeds most often when authentication data or a strong pattern
of prior legitimate use is present. It fails most often when the merchant
submits only order confirmation screenshots, which prove that an order existed
but say nothing about who placed it.
