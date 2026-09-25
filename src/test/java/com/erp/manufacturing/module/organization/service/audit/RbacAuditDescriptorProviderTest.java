package com.erp.manufacturing.module.organization.service.audit;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentResponse;
import com.erp.manufacturing.module.organization.repository.AccessScopeRepository;
import com.erp.manufacturing.module.organization.repository.PermissionRepository;
import com.erp.manufacturing.module.organization.repository.RolePermissionRepository;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RbacAuditDescriptorProvider")
class RbacAuditDescriptorProviderTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final AccessScopeRepository accessScopeRepository = mock(AccessScopeRepository.class);
    private final RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final RbacAuditDescriptorProvider provider = new RbacAuditDescriptorProvider(
            roleRepository, permissionRepository, accessScopeRepository,
            rolePermissionRepository, userRepository);

    @SuppressWarnings("unused")
    static class SampleService {
        public void grantPermission(UUID roleId, UUID permissionId) {
        }

        public UserRoleAssignmentResponse assignRole(Object request) {
            return null;
        }
    }

    private AuditInvocation grantInvocation(UUID roleId, UUID permissionId, AuditAction action,
                                            Object preState) throws Exception {
        Method method = SampleService.class.getMethod("grantPermission", UUID.class, UUID.class);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(action);
        when(auditable.entityType()).thenReturn("Role");
        return new AuditInvocation(method, new Object[]{roleId, permissionId},
                new String[]{"roleId", "permissionId"}, null, null, auditable, Map.of(), preState);
    }

    @Test
    @DisplayName("records BOTH the role and the permission, which the flat columns cannot express")
    void grant_recordsRoleAndPermission() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(Role.builder().roleId(roleId).code("ADMIN").build()));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(
                Permission.builder().permissionId(permissionId).code("PERM_AUDIT_READ").build()));

        AuditRecordDraft.Builder builder = AuditRecordDraft.builder()
                .action(AuditAction.PERMISSION_GRANTED);
        provider.describe(grantInvocation(roleId, permissionId, AuditAction.PERMISSION_GRANTED, false),
                builder);
        AuditRecordDraft draft = builder.build();

        // Before this provider the row named neither participant: entityType was the literal string
        // "Role" with no id, and the permission — the entire subject of the event — was absent.
        assertThat(draft.primaryEntity().entityId()).isEqualTo(roleId.toString());
        assertThat(draft.primaryEntity().entityName()).isEqualTo("ADMIN");
        assertThat(draft.entities()).anySatisfy(entity -> {
            assertThat(entity.entityType()).isEqualTo("Permission");
            assertThat(entity.entityName()).isEqualTo("PERM_AUDIT_READ");
        });
    }

    @Test
    @DisplayName("flags a grant the role already held as a no-op instead of claiming a privilege change")
    void grant_thatChangedNothing_isMarkedNoOp() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());

        AuditRecordDraft.Builder builder = AuditRecordDraft.builder()
                .action(AuditAction.PERMISSION_GRANTED);
        // preState = true: the grant already existed, so grantPermission returned early.
        provider.describe(grantInvocation(roleId, permissionId, AuditAction.PERMISSION_GRANTED, true),
                builder);

        // Decision §11.5: recorded, not suppressed — an administrator did attempt a privilege change
        // and that attempt is worth having. The flag stops a reader mistaking it for a real delta.
        assertThat(builder.build().metadataJson()).isEqualTo("{\"noOp\":true}");
        assertThat(builder.build().reasonCode()).isEqualTo("PERMISSION_ALREADY_GRANTED");
    }

    @Test
    @DisplayName("a grant that really changed something carries no no-op marker")
    void grant_thatChangedSomething_isNotMarked() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());

        AuditRecordDraft.Builder builder = AuditRecordDraft.builder()
                .action(AuditAction.PERMISSION_GRANTED);
        provider.describe(grantInvocation(roleId, permissionId, AuditAction.PERMISSION_GRANTED, false),
                builder);

        assertThat(builder.build().metadataJson()).isNull();
    }

    @Test
    @DisplayName("captures whether the membership existed before the command, which is unrecoverable after")
    void preState_recordsExistingMembership() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId))
                .thenReturn(true);

        Object preState = provider.capturePreState(
                grantInvocation(roleId, permissionId, AuditAction.PERMISSION_GRANTED, null));

        assertThat(preState).isEqualTo(true);
    }

    @Test
    @DisplayName("an assignment records the user, role and scope, not just the assignment id")
    void assignment_recordsAllThreeParticipants() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        Method method = SampleService.class.getMethod("assignRole", Object.class);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn(AuditAction.USER_ROLE_SCOPE_ASSIGNED);
        when(auditable.entityType()).thenReturn("UserRoleAssignment");
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().userId(userId).username("operator").build()));
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(Role.builder().roleId(roleId).code("MANAGER").build()));
        when(accessScopeRepository.findById(scopeId)).thenReturn(Optional.of(
                AccessScope.builder().scopeId(scopeId).code("PLANT-A").build()));

        AuditInvocation invocation = new AuditInvocation(method, new Object[]{new Object()},
                new String[]{"request"},
                new UserRoleAssignmentResponse(assignmentId, userId, roleId, scopeId, "ACTIVE", null),
                null, auditable, Map.of(), null);

        AuditRecordDraft.Builder builder = AuditRecordDraft.builder()
                .action(AuditAction.USER_ROLE_SCOPE_ASSIGNED);
        provider.describe(invocation, builder);
        AuditRecordDraft draft = builder.build();

        // The assignment id alone forced an investigator to join to a table a later revoke may have
        // changed; the participants are snapshotted here instead.
        assertThat(draft.primaryEntity().entityId()).isEqualTo(assignmentId.toString());
        assertThat(draft.entities()).extracting(entity -> entity.entityType() + "=" + entity.entityName())
                .contains("User=operator", "Role=MANAGER", "AccessScope=PLANT-A");
    }
}
