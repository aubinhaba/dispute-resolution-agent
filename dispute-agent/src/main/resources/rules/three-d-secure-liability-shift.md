---
ruleId: shared-3ds
network: ANY
reasonCode: ANY
appliesTo: 10.4, 4837
title: 3-D Secure Authentication and Fraud Liability Shift
---

## Principle

3-D Secure is an authentication protocol that lets the issuer verify the
cardholder during a card-absent transaction. Its effect on disputes is a
liability shift: when authentication succeeds, responsibility for a subsequent
fraud claim moves from the merchant to the issuer. The shift applies to fraud
disputes only. It gives the merchant no protection against a non-receipt or a
not-as-described claim.

## Outcome: authenticated

The issuer verified the cardholder and returned a full authentication result.
Liability for fraud rests with the issuer. A fraud dispute raised on such a
transaction is invalid, and the merchant represents by supplying the
authentication result alone. This is the single strongest defence against a
fraud dispute.

## Outcome: attempted

The merchant requested authentication but the issuer or the directory service
could not complete it. Liability still moves to the issuer, on the principle that
the merchant did everything required of it and the failure was on the issuing
side. Merchants frequently and wrongly assume this outcome offers no protection.

## Outcome: not authenticated

The issuer explicitly rejected the authentication and the merchant proceeded
anyway. Liability stays with the merchant, and the merchant's position is worse
than if it had never attempted authentication, because the rejection is recorded.

## Outcome: not applied

Authentication was never requested. Liability stays with the merchant. Under
European regulation, the absence of strong customer authentication may also make
the transaction non-compliant unless a documented exemption applied, such as low
value, a trusted beneficiary, or a merchant-initiated transaction.

## Exemptions

Where an exemption was claimed, the merchant must be able to evidence which one
and why it applied. An exemption claimed without grounds is treated as
authentication not applied. Merchant-initiated transactions in a recurring
series inherit the authentication status of the initial transaction, provided the
initial transaction was itself authenticated.
