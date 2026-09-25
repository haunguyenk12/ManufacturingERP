package com.erp.manufacturing.config;

import com.erp.manufacturing.common.audit.AuditableAspect;
import com.erp.manufacturing.common.audit.outbox.AuditOutboxProperties;
import com.erp.manufacturing.common.audit.retention.AuditRetentionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;

/**
 * Wiring for the audit pipeline.
 *
 * <p>{@code @EnableConfigurationProperties} is required here rather than being picked up
 * automatically: the application declares {@code @ConfigurationPropertiesScan("com.erp.manufacturing.config")},
 * and {@link AuditOutboxProperties} deliberately lives next to the outbox code it configures rather
 * than being exiled into this package just to satisfy a scan path.
 *
 * <p>{@code @EnableScheduling} is switched on for the first time in this codebase, for the outbox
 * dispatcher. Nothing else uses {@code @Scheduled} today, so this adds exactly one recurring task.
 *
 * <p>{@code @EnableTransactionManagement(order = ...)} exists solely to pin the transaction advisor
 * relative to {@link AuditableAspect}. Both default to {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE},
 * and equal orders resolve arbitrarily — so without this, whether the audit outbox INSERT joins the
 * business transaction would depend on bean registration order rather than on design. Declaring it
 * here makes Boot back off its own {@code @EnableTransactionManagement}; the value is still far below
 * Spring Security's method interceptors, so authorization keeps running outside both.
 */
@Configuration
@EnableScheduling
@EnableTransactionManagement(order = AuditableAspect.TRANSACTION_ADVISOR_ORDER)
@EnableConfigurationProperties({AuditOutboxProperties.class, AuditRetentionProperties.class})
public class AuditConfig {

    /**
     * A single injectable clock so audit timestamps are pinnable in tests.
     *
     * <p>Without it, asserting "the event was stamped at the moment the command ran" means asserting
     * against {@code Instant.now()} inside the code under test, which can only ever be a range check.
     * Declared here rather than as a global bean elsewhere so that it is obvious which subsystem
     * introduced it; any other component is free to inject it.
     */
    @Bean
    public Clock auditClock() {
        return Clock.systemUTC();
    }
}
