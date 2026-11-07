package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** The code is not pattern-checked here: a malformed code is a wrong code and must cost an attempt. */
public record VerifyChallengeRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 16) String code
) {
    @Override
    public String toString() {
        return "VerifyChallengeRequest[challengeId=" + challengeId + ", code=***]";
    }
}
