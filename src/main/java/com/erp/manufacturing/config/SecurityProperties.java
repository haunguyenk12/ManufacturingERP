package com.erp.manufacturing.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Security configuration properties bound from {@code app.security.*}.
 */
@ConfigurationProperties(prefix = "app.security")
@Validated
public record SecurityProperties(
        @NotEmpty List<String> trustedProxies
) {}
