package com.erp.manufacturing.module.auth.dto;

import com.erp.manufacturing.module.auth.domain.PasswordPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code previousId} is required for SETUP (proof that the initial-password login happened) and ignored for RESET. */
public record ChallengeRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotNull PasswordPurpose purpose,
        UUID previousId
) {}
