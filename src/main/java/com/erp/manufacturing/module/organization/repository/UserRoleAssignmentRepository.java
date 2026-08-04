package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.AssignmentStatus;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.ScopeType;
import com.erp.manufacturing.module.organization.domain.UserRoleAssignment;
import com.erp.manufacturing.module.organization.domain.RoleStatus;
import com.erp.manufacturing.module.organization.dto.ScopeResourcePermissionRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    Optional<UserRoleAssignment> findByUserIdAndRoleIdAndScopeIdAndStatus(
            UUID userId, UUID roleId, UUID scopeId, AssignmentStatus status);

    /**
     * {@code C2-4}: {@code GET /access/assignments?userId=&roleId=&scopeId=}. All three filters are
     * optional UUID equality against columns that already live directly on {@code UserRoleAssignment}
     * — no join needed (unlike the B32 permission-resolution queries above, which must cross into
     * {@code Role}/{@code Permission}/{@code AccessScope} to know what a role actually grants).
     */
    @Query("""
            select a from UserRoleAssignment a
            where (:userId is null or a.userId = :userId)
              and (:roleId is null or a.roleId = :roleId)
              and (:scopeId is null or a.scopeId = :scopeId)
            """)
    Page<UserRoleAssignment> search(@Param("userId") UUID userId,
                                    @Param("roleId") UUID roleId,
                                    @Param("scopeId") UUID scopeId,
                                    Pageable pageable);

    @Query("""
            select distinct r.code
            from UserRoleAssignment a
            join Role r on a.roleId = r.roleId
            join AccessScope s on a.scopeId = s.scopeId
            where a.userId = :userId
              and a.status = :assignmentStatus
              and r.status = :roleStatus
              and s.status = :scopeStatus
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    Set<String> findActiveRoleCodesForUser(@Param("userId") UUID userId,
                                           @Param("now") Instant now,
                                           @Param("assignmentStatus") AssignmentStatus assignmentStatus,
                                           @Param("roleStatus") RoleStatus roleStatus,
                                           @Param("scopeStatus") OrganizationStatus scopeStatus);

    @Query("""
            select distinct p.code
            from UserRoleAssignment a
            join Role r on a.roleId = r.roleId
            join RolePermission rp on rp.roleId = r.roleId
            join Permission p on p.permissionId = rp.permissionId
            join AccessScope s on a.scopeId = s.scopeId
            where a.userId = :userId
              and a.status = :assignmentStatus
              and r.status = :roleStatus
              and p.status = :permissionStatus
              and s.status = :scopeStatus
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    Set<String> findActivePermissionCodesForUser(@Param("userId") UUID userId,
                                                 @Param("now") Instant now,
                                                 @Param("assignmentStatus") AssignmentStatus assignmentStatus,
                                                 @Param("roleStatus") RoleStatus roleStatus,
                                                 @Param("permissionStatus") OrganizationStatus permissionStatus,
                                                 @Param("scopeStatus") OrganizationStatus scopeStatus);

    @Query("""
            select count(a) > 0
            from UserRoleAssignment a
            join Role r on a.roleId = r.roleId
            join RolePermission rp on rp.roleId = r.roleId
            join Permission p on p.permissionId = rp.permissionId
            join AccessScope s on a.scopeId = s.scopeId
            where a.userId = :userId
              and p.code = :permissionCode
              and s.scopeType = :scopeType
              and a.status = :assignmentStatus
              and r.status = :roleStatus
              and p.status = :permissionStatus
              and s.status = :scopeStatus
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    boolean existsActivePermissionInScopeType(@Param("userId") UUID userId,
                                              @Param("permissionCode") String permissionCode,
                                              @Param("scopeType") ScopeType scopeType,
                                              @Param("now") Instant now,
                                              @Param("assignmentStatus") AssignmentStatus assignmentStatus,
                                              @Param("roleStatus") RoleStatus roleStatus,
                                              @Param("permissionStatus") OrganizationStatus permissionStatus,
                                              @Param("scopeStatus") OrganizationStatus scopeStatus);

    @Query("""
            select count(a) > 0
            from UserRoleAssignment a
            join Role r on a.roleId = r.roleId
            join RolePermission rp on rp.roleId = r.roleId
            join Permission p on p.permissionId = rp.permissionId
            join AccessScope s on a.scopeId = s.scopeId
            join AccessScopeResource sr on sr.scopeId = s.scopeId
            where a.userId = :userId
              and p.code = :permissionCode
              and sr.resourceType = :resourceType
              and sr.resourceId = :resourceId
              and a.status = :assignmentStatus
              and r.status = :roleStatus
              and p.status = :permissionStatus
              and s.status = :scopeStatus
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    boolean existsActiveResourcePermission(@Param("userId") UUID userId,
                                           @Param("permissionCode") String permissionCode,
                                           @Param("resourceType") ScopeResourceType resourceType,
                                           @Param("resourceId") UUID resourceId,
                                           @Param("now") Instant now,
                                           @Param("assignmentStatus") AssignmentStatus assignmentStatus,
                                           @Param("roleStatus") RoleStatus roleStatus,
                                           @Param("permissionStatus") OrganizationStatus permissionStatus,
                                           @Param("scopeStatus") OrganizationStatus scopeStatus);

    /**
     * Every (scopeType, resourceType, resourceId, permissionCode) tuple the user's active
     * assignments grant — the data {@code GET /api/v1/auth/me} groups into {@code scopes[]}.
     *
     * <p>{@code left join AccessScopeResource} is deliberate, not {@code inner join}: a
     * {@code GLOBAL} scope has no resource row at all, and an inner join would silently drop it
     * from the result instead of surfacing it with {@code resourceType/resourceId = null}.
     *
     * <p>Repeats the same 5 filters as {@link #findActivePermissionCodesForUser} (invariant B32) —
     * any new query over {@code UserRoleAssignment} must apply all five, not a subset.
     */
    @Query("""
            select distinct new com.erp.manufacturing.module.organization.dto.ScopeResourcePermissionRow(
                    s.scopeType, res.resourceType, res.resourceId, p.code)
            from UserRoleAssignment a
            join Role r on a.roleId = r.roleId
            join RolePermission rp on rp.roleId = r.roleId
            join Permission p on p.permissionId = rp.permissionId
            join AccessScope s on a.scopeId = s.scopeId
            left join AccessScopeResource res on res.scopeId = s.scopeId
            where a.userId = :userId
              and a.status = :assignmentStatus
              and r.status = :roleStatus
              and p.status = :permissionStatus
              and s.status = :scopeStatus
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    List<ScopeResourcePermissionRow> findActiveScopeResourcePermissionRowsForUser(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("assignmentStatus") AssignmentStatus assignmentStatus,
            @Param("roleStatus") RoleStatus roleStatus,
            @Param("permissionStatus") OrganizationStatus permissionStatus,
            @Param("scopeStatus") OrganizationStatus scopeStatus);
}
