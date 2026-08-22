package com.erp.manufacturing.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    @Test
    void validProductionSettingsPass() {
        assertThatCode(() -> validator(validJwt(), validCors(), true,
                List.of("10.20.30.40/32")).validate()).doesNotThrowAnyException();
    }

    @Test
    void knownPlaceholderSecretFailsClosed() {
        JwtProperties jwt = new JwtProperties("change-me-with-more-than-thirty-two-characters",
                "manufacturing-erp", "erp-clients", 900_000L, 604_800_000L, 2_592_000_000L);
        assertThatThrownBy(() -> validator(jwt, validCors(), true, List.of("127.0.0.1")).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void disabledRateLimitFailsClosed() {
        assertThatThrownBy(() -> validator(validJwt(), validCors(), false,
                List.of("127.0.0.1")).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Rate limiting");
    }

    @Test
    void localhostCorsFailsClosed() {
        CorsProperties cors = new CorsProperties(List.of("http://localhost:3000"), List.of("GET"),
                List.of("Authorization"), List.of(), true, 60);
        assertThatThrownBy(() -> validator(validJwt(), cors, true, List.of("127.0.0.1")).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CORS");
    }

    @Test
    void broadTrustedProxyRangeFailsClosed() {
        assertThatThrownBy(() -> validator(validJwt(), validCors(), true,
                List.of("10.0.0.0/8")).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("trusted proxies");
    }

    private ProductionSecurityValidator validator(JwtProperties jwt, CorsProperties cors,
                                                   boolean rateLimitEnabled, List<String> proxies) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "a-strong-redis-password")
                .withProperty("spring.datasource.username", "manufacturing_erp")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("management.endpoints.web.exposure.include", "health,info")
                .withProperty("logging.level.org.hibernate.SQL", "WARN")
                .withProperty("logging.level.org.hibernate.orm.jdbc.bind", "WARN");
        environment.setActiveProfiles("prod");
        return new ProductionSecurityValidator(jwt, cors,
                new RateLimitProperties(rateLimitEnabled, List.of()),
                new SecurityProperties(proxies), environment);
    }

    private JwtProperties validJwt() {
        return new JwtProperties("M9!pQ2#sV7@kL4$xN8&cR5*zT1-wY6_u",
                "manufacturing-erp", "erp-clients", 900_000L, 604_800_000L, 2_592_000_000L);
    }

    private CorsProperties validCors() {
        return new CorsProperties(List.of("https://erp.example.com"), List.of("GET", "POST"),
                List.of("Authorization", "Content-Type"), List.of("X-Trace-Id"), true, 3600);
    }
}
