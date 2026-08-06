package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Rule R2: every deny branch is paired with {@code verify(...)} pinning the exact permission string,
 * plus at least one allow branch — a deny-only test cannot catch a typo'd {@code PERM_AUDIT_READ}
 * (the mock would return {@code false} by default either way).
 */
@SpringJUnitConfig(AuditLogMethodSecurityTest.Config.class)
@DisplayName("AuditLogQueryService method security")
class AuditLogMethodSecurityTest {

    @Autowired AuditLogQueryService auditLogQueryService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AuditLogChangeRepository auditLogChangeRepository;

    private static final UUID AUDIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, auditLogRepository, auditLogChangeRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_deniedWhenAuditReadMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_AUDIT_READ"))).thenReturn(false);

        assertThatThrownBy(() -> auditLogQueryService.list(
                null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(auditLogRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_AUDIT_READ"));
    }

    @Test
    void get_deniedWhenAuditReadMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_AUDIT_READ"))).thenReturn(false);

        assertThatThrownBy(() -> auditLogQueryService.get(AUDIT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(auditLogRepository, auditLogChangeRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_AUDIT_READ"));
    }

    @Test
    void list_allowedWhenAuditReadPresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_AUDIT_READ"))).thenReturn(true);
        when(auditLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        assertThatCode(() -> auditLogQueryService.list(
                null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void get_allowedWhenAuditReadPresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_AUDIT_READ"))).thenReturn(true);
        AuditLog auditLog = AuditLog.builder()
                .auditId(AUDIT_ID)
                .action("LOGIN")
                .status("SUCCESS")
                .build();
        when(auditLogRepository.findById(AUDIT_ID)).thenReturn(Optional.of(auditLog));
        when(auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(AUDIT_ID)).thenReturn(List.of());

        assertThatCode(() -> auditLogQueryService.get(AUDIT_ID)).doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AuditLogQueryService auditLogQueryService(AuditLogRepository auditLogRepository,
                                                   AuditLogChangeRepository auditLogChangeRepository) {
            return new AuditLogQueryService(auditLogRepository, auditLogChangeRepository);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean AuditLogRepository auditLogRepository() { return mock(AuditLogRepository.class); }

        @Bean AuditLogChangeRepository auditLogChangeRepository() { return mock(AuditLogChangeRepository.class); }
    }
}
