package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.module.user.domain.UserPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides the current user's UUID to Spring Data JPA Auditing.
 * Populates {@code created_by} and {@code updated_by} on all {@link BaseEntity} subclasses.
 */
@Component("auditorAware")
public class SecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(p -> p instanceof UserPrincipal)
                .map(p -> ((UserPrincipal) p).getUserId());
    }
}
