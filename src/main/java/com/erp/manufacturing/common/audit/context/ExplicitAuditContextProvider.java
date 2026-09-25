package com.erp.manufacturing.common.audit.context;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Context pushed explicitly by code that has no request to read from: scheduled jobs, message
 * consumers, batch/import workers, maintenance tasks (AR-3).
 *
 * <p>Usage is scoped, never "set and hope somebody clears it":
 *
 * <pre>{@code
 * ExplicitAuditContextProvider.runWith(AuditContext.system("RetentionJob"), () -> {
 *     ...work that emits audit events...
 * });
 * }</pre>
 *
 * <p>The value is restored rather than cleared on exit, so nesting works and an inner scope cannot
 * wipe the outer one. A {@code ThreadLocal} is appropriate here precisely because the scope is
 * lexical: nothing hands this across a thread boundary, and anything that needs to cross one carries
 * an immutable {@link AuditContext} instead.
 */
@Component
public class ExplicitAuditContextProvider implements AuditContextProvider {

    private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

    public static void runWith(AuditContext context, Runnable work) {
        callWith(context, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T callWith(AuditContext context, Supplier<T> work) {
        AuditContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Optional<AuditContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
