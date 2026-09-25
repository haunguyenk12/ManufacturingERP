package com.erp.manufacturing.common.audit.context;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Picks the first {@link AuditContextProvider} that can describe the current execution (AR-3).
 *
 * <p>Always returns a context. There is no "no context" branch for callers to forget: an event with
 * an unknown actor is still an event worth recording, and dropping it would reintroduce exactly the
 * silence this refactor exists to remove.
 */
@Component
public class AuditContextResolver {

    private final List<AuditContextProvider> providers;

    public AuditContextResolver(List<AuditContextProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(AuditContextProvider::order))
                .toList();
    }

    public AuditContext resolve() {
        return providers.stream()
                .map(AuditContextProvider::current)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseGet(() -> AuditContext.system("application"));
    }
}
