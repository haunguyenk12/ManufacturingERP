package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteChallengeRequest(
        @NotBlank @Size(max = 200) String setupToken,
        @NotBlank @Size(max = 255) String password
) {
    @Override
    public String toString() {
        return "CompleteChallengeRequest[setupToken=***, password=***]";
    }
}
