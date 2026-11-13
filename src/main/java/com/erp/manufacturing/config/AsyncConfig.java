package com.erp.manufacturing.config;

import com.erp.manufacturing.common.context.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration.
 * The "auditExecutor" bean is dedicated to audit log persistence –
 * it propagates MDC context to async threads via {@link MdcTaskDecorator}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        // CallerRunsPolicy: if queue is full, caller thread processes the task
        // prevents audit log loss under high load
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Outgoing account emails. Separate from {@code auditExecutor} so a slow SMTP server (each send
     * can block for the 5 s mail timeouts) never occupies the audit threads.
     */
    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
