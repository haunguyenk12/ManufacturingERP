package com.erp.manufacturing.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /admin/users/v1/accounts/commands}. Only {@code kind = CREATE} exists so far;
 * the discriminator is kept so the other account commands can join the same endpoint.
 */
public record CreateAccountCommand(
        @NotBlank String kind,

        @NotBlank @Size(min = 3, max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._@+-]+$", message = "Username may only contain letters, digits and . _ @ + -")
        String username,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank @Size(max = 255) String password
) {
    public static final String CREATE = "CREATE";

    @Override
    public String toString() {
        return "CreateAccountCommand[kind=" + kind + ", username=" + username + ", email=" + email + ", password=***]";
    }
}
