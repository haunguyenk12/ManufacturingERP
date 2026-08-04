package com.erp.manufacturing.module.uom.service;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.dto.UomCreateRequest;
import com.erp.manufacturing.module.uom.mapper.UomMapper;
import com.erp.manufacturing.module.uom.repository.UomRepository;
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
 * Rule R2: every deny branch is paired with {@code verify(...)} that pins the exact permission
 * string, and there is at least one allow branch — a deny-only test cannot catch a typo'd
 * {@code PERM_UOM_*} string (the mock would return {@code false} by default either way).
 */
@SpringJUnitConfig(UomMethodSecurityTest.Config.class)
@DisplayName("UomService method security")
class UomMethodSecurityTest {

    @Autowired UomService uomService;
    @Autowired PermissionGuard permissionGuard;
    @Autowired UomRepository uomRepository;

    private static final UUID UOM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, uomRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deniedWhenUomManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> uomService.create(new UomCreateRequest("KG", "Kilogram", null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(uomRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_UOM_MANAGE"));
    }

    @Test
    void activate_deniedWhenUomManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> uomService.activate(UOM_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(uomRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_UOM_MANAGE"));
    }

    @Test
    void deactivate_deniedWhenUomManageMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_MANAGE"))).thenReturn(false);

        assertThatThrownBy(() -> uomService.deactivate(UOM_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(uomRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_UOM_MANAGE"));
    }

    @Test
    void get_deniedWhenUomReadMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_READ"))).thenReturn(false);

        assertThatThrownBy(() -> uomService.get(UOM_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(uomRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_UOM_READ"));
    }

    @Test
    void list_deniedWhenUomReadMissing() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_READ"))).thenReturn(false);

        assertThatThrownBy(() -> uomService.list(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(uomRepository);
        verify(permissionGuard).hasPermission(any(), eq("PERM_UOM_READ"));
    }

    @Test
    void list_allowedWhenUomReadPresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_READ"))).thenReturn(true);
        when(uomRepository.search(any(), any(), any())).thenReturn(Page.empty());

        assertThatCode(() -> uomService.list(null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void create_allowedWhenUomManagePresent() {
        when(permissionGuard.hasPermission(any(), eq("PERM_UOM_MANAGE"))).thenReturn(true);
        when(uomRepository.existsByCode("KG")).thenReturn(false);
        when(uomRepository.save(any(Uom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> uomService.create(new UomCreateRequest("KG", "Kilogram", null)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        UomService uomService(UomRepository uomRepository, UomMapper mapper) {
            return new UomService(uomRepository, mapper);
        }

        @Bean UomMapper uomMapper() { return new UomMapper(); }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() { return mock(PermissionGuard.class); }

        @Bean UomRepository uomRepository() { return mock(UomRepository.class); }
    }
}
