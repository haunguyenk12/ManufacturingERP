package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
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
import static org.mockito.Mockito.*;

@SpringJUnitConfig(InventoryReservationQueryServiceMethodSecurityTest.Config.class)
@DisplayName("InventoryReservationQueryService method security (G4-4)")
class InventoryReservationQueryServiceMethodSecurityTest {

    @Autowired InventoryReservationQueryService service;
    @Autowired PermissionGuard permissionGuard;
    @Autowired MaterialReservationRepository reservationRepository;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reset(permissionGuard, reservationRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_deniedWhenWarehouseReadScopeMissing() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.list(WAREHOUSE_ID, null, null, MaterialReservationStatus.ACTIVE,
                PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID));
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void list_allowedWhenWarehouseReadScopePresent() {
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_INVENTORY_READ"), eq("WAREHOUSE"), eq(WAREHOUSE_ID)))
                .thenReturn(true);
        when(reservationRepository.searchByWarehouse(eq(WAREHOUSE_ID), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        assertThatCode(() -> service.list(WAREHOUSE_ID, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        InventoryReservationQueryService inventoryReservationQueryService(MaterialReservationRepository repository) {
            return new InventoryReservationQueryService(repository);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        MaterialReservationRepository materialReservationRepository() {
            return mock(MaterialReservationRepository.class);
        }
    }
}
