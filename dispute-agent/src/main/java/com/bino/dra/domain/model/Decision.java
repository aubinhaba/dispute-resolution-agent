package com.bino.dra.domain.model;

/**
 * Final decision on a dispute.
 *
 * <ul>
 *   <li>{@link #REPRESENT} — fight the chargeback (solid evidence to present).</li>
 *   <li>{@link #ACCEPT}    — accept it (fighting would lose / not be worth it).</li>
 *   <li>{@link #ESCALATE}  — hand off to human review.</li>
 * </ul>
 */
public enum Decision {
    REPRESENT,
    ACCEPT,
    ESCALATE
}
