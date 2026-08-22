package com.erp.manufacturing.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Fails startup when the production profile contains a known-dangerous security setting. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecurityValidator {

    private static final long MAX_ACCESS_TOKEN_MS = 15 * 60 * 1000L;

    private final JwtProperties jwt;
    private final CorsProperties cors;
    private final RateLimitProperties rateLimit;
    private final SecurityProperties security;
    private final Environment environment;

    @PostConstruct
    void validate() {
        require(Arrays.asList(environment.getActiveProfiles()).contains("prod"),
                "The production validator requires the prod profile");

        String normalizedSecret = jwt.secret().toLowerCase(Locale.ROOT);
        require(jwt.secret().getBytes(StandardCharsets.UTF_8).length >= 32,
                "JWT_SECRET must contain at least 32 bytes");
        require(!normalizedSecret.contains("change-me")
                        && !normalizedSecret.contains("dev-only")
                        && !normalizedSecret.contains("replace-with"),
                "JWT_SECRET is a known placeholder or development secret");
        require(jwt.accessTokenExpiryMs() <= MAX_ACCESS_TOKEN_MS,
                "JWT access token lifetime must not exceed 15 minutes in production");
        require(jwt.absoluteSessionTimeoutMs() >= jwt.refreshTokenExpiryMs(),
                "Absolute session timeout must be at least one refresh-token lifetime");

        require(rateLimit.enabled(), "Rate limiting must be enabled in production");
        cors.validate();
        require(cors.allowedOrigins().stream().noneMatch(this::isLocalOrigin),
                "Production CORS origins must not contain localhost or loopback addresses");

        String redisPassword = environment.getProperty("spring.data.redis.password", "");
        require(!redisPassword.isBlank(), "Redis authentication is mandatory in production");
        String databaseUser = environment.getProperty("spring.datasource.username", "");
        require(!databaseUser.equalsIgnoreCase("postgres"),
                "The application database user must not be the PostgreSQL superuser");
        require(security.trustedProxies().stream().noneMatch(this::isBroadPrivateRange),
                "Production trusted proxies must name the actual proxy, not an entire private network");

        require(!environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false),
                "OpenAPI documents must be disabled in production");
        require(!environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false),
                "Swagger UI must be disabled in production");
        String exposedEndpoints = environment.getProperty(
                "management.endpoints.web.exposure.include", "health,info");
        require(Arrays.stream(exposedEndpoints.split(","))
                        .map(String::trim).allMatch(value -> value.equals("health") || value.equals("info")),
                "Only health and info Actuator endpoints may be exposed in production");

        String sqlLevel = environment.getProperty("logging.level.org.hibernate.SQL", "INFO");
        String bindLevel = environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind", "INFO");
        require(!isVerboseSqlLevel(sqlLevel) && !isVerboseSqlLevel(bindLevel),
                "Hibernate SQL and bind logging must not be DEBUG/TRACE in production");
    }

    private boolean isLocalOrigin(String origin) {
        String value = origin.toLowerCase(Locale.ROOT);
        return value.contains("localhost") || value.contains("127.0.0.1") || value.contains("[::1]");
    }

    private boolean isBroadPrivateRange(String range) {
        return range.equals("10.0.0.0/8")
                || range.equals("172.16.0.0/12")
                || range.equals("192.168.0.0/16");
    }

    private boolean isVerboseSqlLevel(String level) {
        return "DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Unsafe production configuration: " + message);
        }
    }
}
