package com.erp.manufacturing.module.auth.domain;

public enum PasswordChallengeStatus {
    /** Code emailed, not yet exchanged. */
    PENDING,
    /** Code exchanged for a setup token; the token is not yet spent. */
    VERIFIED,
    /** Setup token spent and the password changed. */
    COMPLETED,
    /** Replaced by a newer challenge; its code no longer works. */
    SUPERSEDED
}
