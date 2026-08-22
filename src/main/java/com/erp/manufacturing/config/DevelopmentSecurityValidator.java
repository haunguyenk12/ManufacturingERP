package com.erp.manufacturing.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Prevents a copied .env.example from being mistaken for usable development credentials. */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevelopmentSecurityValidator {

    private final JwtProperties jwt;
    private final Environment environment;

    @PostConstruct
    void validate() {
        requireSecret("JWT_SECRET", jwt.secret());
        requireSecret("DB_PASSWORD", environment.getProperty("spring.datasource.password", ""));
        requireSecret("REDIS_PASSWORD", environment.getProperty("spring.data.redis.password", ""));
    }

    private void requireSecret(String name, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (value.getBytes(StandardCharsets.UTF_8).length < 16
                || normalized.contains("replace-with") || normalized.contains("replace_me")) {
            throw new IllegalStateException(name + " must be replaced with a local random value");
        }
    }
}
