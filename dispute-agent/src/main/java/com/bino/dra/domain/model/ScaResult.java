package com.bino.dra.domain.model;

/**
 * Strong-authentication (SCA / 3-D Secure) result.
 *
 * <p>3DS triggers a liability shift: {@link #AUTHENTICATED} moves fraud liability to the issuer
 * (strong REPRESENT argument), whereas {@link #NOT_AUTHENTICATED} usually leaves it on the merchant.
 */
public enum ScaResult {
    AUTHENTICATED,
    ATTEMPTED,
    NOT_AUTHENTICATED,
    NOT_APPLIED
}
