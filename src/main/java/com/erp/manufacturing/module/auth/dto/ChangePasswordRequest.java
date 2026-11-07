package com.erp.manufacturing.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 255) String currentPassword,
        @NotBlank @Size(max = 255) String password
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, password=***]";
    }
}
