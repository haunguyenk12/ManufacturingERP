package com.erp.manufacturing.module.organization.service.audit;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.spi.AuditDescriptorProvider;
import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import com.erp.manufacturing.module.organization.domain.AccessScope;
import com.erp.manufacturing.module.organization.domain.Permission;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentRequest;
import com.erp.manufacturing.module.organization.dto.UserRoleAssignmentResponse;
import com.erp.manufacturing.module.organization.repository.AccessScopeRepository;
import com.erp.manufacturing.module.organization.repository.PermissionRepository;
import com.erp.manufacturing.module.organization.repository.RolePermissionRepository;
import com.erp.manufacturing.module.organization.repository.RoleRepository;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Describes the RBAC commands that touch more than one object (AR-4/AR-5).
 *
 * <p><strong>What was wrong.</strong> {@code grantPermission(roleId, permissionId)} and
 * {@code revokePermission(...)} are annotated {@code entityType = "Role"} with no id expression, and
 * they return {@code void}. The resulting audit row therefore named neither the role nor the
 * permission: the trail could report that <em>a</em> permission change happened and nothing else — on
 * the one class of action where "who got which privilege" is the entire question. Assignments were
 * only marginally better: the row carried the assignment id, so answering "which user, which role,
 * which scope" meant joining to a table that a revoke may since have changed.
 *
 * <p>Every event now carries the full participant set: the role as primary target, and the permission
 * / user / scope as related targets, each with its code snapshotted at the time.
 *
 * <p><strong>No-op semantics</strong> (decision §11.5): a grant of a permission the role already
 * holds, or a revoke of one it does not, is recorded as {@code SUCCESS} with
 * {@code metadata.noOp = true} rather than suppressed. An administrator did attempt a privilege
 * change, and that attempt is worth having; silently dropping it would leave a gap in the trail
 * exactly where someone was probing what they could do. The flag keeps a reader from mistaking it for
 * an actual privilege delta. Detection uses {@code capturePreState}, because after the call both
 * cases look identical.
 */
@Component
@RequiredArgsConstructor
public class RbacAuditDescriptorProvider implements AuditDescriptorProvider {

    private static final Set<AuditAction> PERMISSION_ACTIONS =
            Set.of(AuditAction.PERMISSION_GRANTED, AuditAction.PERMISSION_REVOKED);
    private static final Set<AuditAction> ASSIGNMENT_ACTIONS =
            Set.of(AuditAction.USER_ROLE_SCOPE_ASSIGNED, AuditAction.USER_ROLE_SCOPE_REVOKED);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AccessScopeRepository accessScopeRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @Override
    public boolean supports(AuditInvocation invocation) {
        AuditAction action = invocation.action();
        return PERMISSION_ACTIONS.contains(action) || ASSIGNMENT_ACTIONS.contains(action);
    }

    /** For grant/revoke: did the membership already exist before the command ran? */
    @Override
    public Object capturePreState(AuditInvocation invocation) {
        if (!PERMISSION_ACTIONS.contains(invocation.action())) {
            return null;
        }
        UUID roleId = invocation.argument("roleId").filter(UUID.class::isInstance)
                .map(UUID.class::cast).orElse(null);
        UUID permissionId = invocation.argument("permissionId").filter(UUID.class::isInstance)
                .map(UUID.class::cast).orElse(null);
        if (roleId == null || permissionId == null) {
            return null;
        }
        return rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId);
    }

    @Override
    public void describe(AuditInvocation invocation, AuditRecordDraft.Builder builder) {
        if (PERMISSION_ACTIONS.contains(invocation.action())) {
            describePermissionChange(invocation, builder);
            return;
        }
        describeAssignment(invocation, builder);
    }

    private void describePermissionChange(AuditInvocation invocation, AuditRecordDraft.Builder builder) {
        UUID roleId = uuidArgument(invocation, "roleId");
        UUID permissionId = uuidArgument(invocation, "permissionId");

        if (roleId != null) {
            builder.primaryEntity("Role", roleId, roleRepository.findById(roleId)
                    .map(Role::getCode).orElse(null));
        }
        if (permissionId != null) {
            builder.relatedEntity("Permission", permissionId, permissionRepository.findById(permissionId)
                    .map(Permission::getCode).orElse(null));
        }

        boolean granting = invocation.action() == AuditAction.PERMISSION_GRANTED;
        invocation.preState(Boolean.class).ifPresent(existedBefore -> {
            boolean noOp = granting == existedBefore;
            if (noOp) {
                builder.metadataJson("{\"noOp\":true}")
                        .reasonCode(granting ? "PERMISSION_ALREADY_GRANTED" : "PERMISSION_NOT_HELD");
            }
        });
    }

    private void describeAssignment(AuditInvocation invocation, AuditRecordDraft.Builder builder) {
        UUID userId = null;
        UUID roleId = null;
        UUID scopeId = null;

        if (invocation.result() instanceof UserRoleAssignmentResponse response) {
            builder.primaryEntity("UserRoleAssignment", response.assignmentId(), null);
            userId = response.userId();
            roleId = response.roleId();
            scopeId = response.scopeId();
        } else if (invocation.argumentOfType(UserRoleAssignmentRequest.class).isPresent()) {
            // Failure path: the command threw, so there is no response to read the triple from, but
            // the request still says exactly what was attempted — which is the interesting part.
            UserRoleAssignmentRequest request =
                    invocation.argumentOfType(UserRoleAssignmentRequest.class).orElseThrow();
            userId = request.userId();
            roleId = request.roleId();
            scopeId = request.scopeId();
        }

        if (userId != null) {
            UUID finalUserId = userId;
            builder.relatedEntity("User", userId, userRepository.findById(finalUserId)
                    .map(user -> user.getUsername()).orElse(null));
        }
        if (roleId != null) {
            builder.relatedEntity("Role", roleId, roleRepository.findById(roleId)
                    .map(Role::getCode).orElse(null));
        }
        if (scopeId != null) {
            builder.relatedEntity("AccessScope", scopeId, accessScopeRepository.findById(scopeId)
                    .map(AccessScope::getCode).orElse(null));
        }
    }

    private UUID uuidArgument(AuditInvocation invocation, String name) {
        return invocation.argument(name)
                .filter(UUID.class::isInstance)
                .map(UUID.class::cast)
                .orElse(null);
    }
}
