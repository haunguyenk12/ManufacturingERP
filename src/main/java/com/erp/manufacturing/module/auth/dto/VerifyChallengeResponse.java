package com.erp.manufacturing.module.auth.dto;

/** Restricted credential: usable once, only to set a password. */
public record VerifyChallengeResponse(String setupToken) {
    @Override
    public String toString() {
        return "VerifyChallengeResponse[setupToken=***]";
    }
}
