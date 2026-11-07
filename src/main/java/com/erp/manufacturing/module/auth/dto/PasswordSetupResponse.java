package com.erp.manufacturing.module.auth.dto;

/**
 * Login answer for an account still in {@code PENDING_SETUP}: no token of any kind, only the
 * challenge the emailed code belongs to.
 */
public record PasswordSetupResponse(String kind, String email, ChallengeResponse challenge) implements LoginResult {

    public static final String KIND = "PASSWORD_SETUP";

    public PasswordSetupResponse(String email, ChallengeResponse challenge) {
        this(KIND, email, challenge);
    }
}
