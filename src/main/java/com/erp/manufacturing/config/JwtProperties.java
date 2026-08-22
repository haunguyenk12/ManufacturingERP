package com.erp.manufacturing.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties bound from {@code app.jwt.*}.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @Positive Long accessTokenExpiryMs,
        @NotNull @Positive Long refreshTokenExpiryMs,
        /**
         * Absolute session lifetime (D8b). Measured from login and never extended by a refresh,
         * unlike {@code refreshTokenExpiryMs} which slides on every rotation.
         */
        @NotNull @Positive Long absoluteSessionTimeoutMs
) {
    @ConstructorBinding
    public JwtProperties {
        // Explicitly select the canonical record constructor when compatibility overloads exist.
    }

    /** Backward-compatible constructor for focused unit tests. */
    public JwtProperties(String secret, Long accessTokenExpiryMs, Long refreshTokenExpiryMs,
                         Long absoluteSessionTimeoutMs) {
        this(secret, "manufacturing-erp", "manufacturing-erp-clients",
                accessTokenExpiryMs, refreshTokenExpiryMs, absoluteSessionTimeoutMs);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, issuer=" + issuer + ", audience=" + audience
                + ", accessTokenExpiryMs=" + accessTokenExpiryMs
                + ", refreshTokenExpiryMs=" + refreshTokenExpiryMs
                + ", absoluteSessionTimeoutMs=" + absoluteSessionTimeoutMs + "]";
    }
}
