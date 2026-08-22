package com.erp.manufacturing.module.organization.security;

import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.organization.dto.AccessScopeResponse;
import com.erp.manufacturing.module.organization.dto.PermissionResponse;
import com.erp.manufacturing.module.organization.dto.RoleResponse;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentResponse;
import com.erp.manufacturing.module.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins data minimization for identity, RBAC and audit responses. */
@DisplayName("Sensitive response data minimization")
class SensitiveResponseDataMinimizationSecurityTest {

    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "password", "credential", "secret", "accesstoken", "refreshtoken",
            "tokenhash", "passwordhash", "rediskey", "authversion");

    @Test
    void identityAndRbacResponsesNeverExposeAuthenticationSecrets() {
        List<Class<?>> responseTypes = List.of(
                UserResponse.class,
                MeResponse.class,
                RoleResponse.class,
                PermissionResponse.class,
                AccessScopeResponse.class,
                UserRoleAssignmentResponse.class,
                AuditLogResponse.class,
                AuditLogDetailResponse.class);
        List<String> violations = new ArrayList<>();

        for (Class<?> responseType : responseTypes) {
            assertThat(responseType.isRecord())
                    .as(responseType.getName() + " must remain an explicit record DTO")
                    .isTrue();
            for (RecordComponent component : responseType.getRecordComponents()) {
                String normalized = component.getName().toLowerCase(Locale.ROOT);
                FORBIDDEN_FRAGMENTS.stream()
                        .filter(normalized::contains)
                        .forEach(fragment -> violations.add(
                                responseType.getSimpleName() + "." + component.getName()
                                        + " contains forbidden fragment " + fragment));
            }
        }

        assertThat(violations)
                .as("Identity/RBAC/audit DTOs must not expose authentication secrets")
                .isEmpty();
    }
}
