package com.erp.manufacturing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Rate limiting rule configuration bound from {@code app.rate-limit.*}.
 * Rules are ordered; the first matching BLOCK rule wins.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public record RateLimitProperties(
        boolean enabled,
        @Valid List<RateLimitRule> rules
) {

    public record RateLimitRule(
            @NotBlank String id,
            @NotNull Scope scope,
            @NotBlank String pattern,
            @Positive int limit,
            @Positive int windowSeconds,
            @NotNull Action action
    ) {}

    public enum Scope  { IP, USER, ENDPOINT }
    public enum Action { BLOCK, LOG_ONLY }
}
