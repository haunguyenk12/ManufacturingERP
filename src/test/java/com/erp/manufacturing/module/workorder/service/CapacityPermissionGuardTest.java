package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G5 (FE §1.4): a preview reads routing, BOM, work centers, calendars and shifts, so each of the six
 * permissions is required on its own scope — dropping any one of them must refuse the preview.
 */
@DisplayName("CapacityPermissionGuard.canPreview")
class CapacityPermissionGuardTest {

    private final PermissionGuard permissionGuard = mock(PermissionGuard.class);
    private final OrganizationLookupService organizationLookupService = mock(OrganizationLookupService.class);
    private final CapacityPermissionGuard guard = new CapacityPermissionGuard(permissionGuard, organizationLookupService);
    private final Authentication authentication = mock(Authentication.class);

    private final UUID plantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(organizationLookupService.getPlant(plantId)).thenReturn(
                Plant.builder().plantId(plantId).company(Company.builder().companyId(companyId).build()).build());
    }

    @Test
    void allSixPermissionsOnTheirScopes_allowThePreview() {
        grantAllExcept(null);

        assertThat(guard.canPreview(authentication, plantId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERM_CAPACITY_READ", "PERM_WORK_CENTER_READ", "PERM_WORK_CALENDAR_READ",
            "PERM_SHIFT_READ", "PERM_ROUTING_READ", "PERM_BOM_READ"})
    void anyOneMissing_refusesThePreview(String missing) {
        grantAllExcept(missing);

        assertThat(guard.canPreview(authentication, plantId)).isFalse();
    }

    @Test
    void unknownPlant_isRefused() {
        UUID unknown = UUID.randomUUID();
        when(organizationLookupService.getPlant(unknown)).thenThrow(new IllegalArgumentException("missing"));

        assertThat(guard.canPreview(authentication, unknown)).isFalse();
    }

    private void grantAllExcept(String missing) {
        for (String permission : new String[]{"PERM_CAPACITY_READ", "PERM_WORK_CENTER_READ",
                "PERM_WORK_CALENDAR_READ", "PERM_SHIFT_READ"}) {
            when(permissionGuard.hasResourceAccess(any(), eq(permission), eq("PLANT"), eq(plantId)))
                    .thenReturn(!permission.equals(missing));
        }
        for (String permission : new String[]{"PERM_ROUTING_READ", "PERM_BOM_READ"}) {
            when(permissionGuard.hasResourceAccess(any(), eq(permission), eq("COMPANY"), eq(companyId)))
                    .thenReturn(!permission.equals(missing));
        }
    }
}
