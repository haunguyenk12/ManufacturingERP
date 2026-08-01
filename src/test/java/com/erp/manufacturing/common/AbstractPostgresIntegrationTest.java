package com.erp.manufacturing.common;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton Testcontainers pattern: the container starts once per JVM (static
 * initializer) and is never stopped explicitly — Ryuk reaps it when the JVM exits.
 * This shares a single Postgres container across every *IT class in a `mvn verify`
 * run, instead of paying container startup cost per class (see NEXT_PHASE_PLAN.md D1).
 */
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
