package com.erp.manufacturing.common.audit.controller;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogQueryService;
import com.erp.manufacturing.common.audit.AuditLogSearchCriteria;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit Logs read API (ADMIN only).
 *
 * <p>{@code PERM_AUDIT_READ} is checked as a global permission
 * ({@code @permissionGuard.hasPermission}, not {@code hasResourceAccess}) — the audit trail is not
 * owned by a single company or plant, so there is no {@code X-Plant-Id} cross-check on the optional
 * {@code plantId} filter here. The cross-check in {@code error-handling.md §5.6.1} exists to keep a
 * plant-scoped caller's session and URL consistent; that reasoning does not apply to a global
 * admin-only read.
 *
 * <p>The AR-6 parameters ({@code outcome}, {@code source}, {@code companyId}, {@code warehouseId},
 * {@code relatedEntityType}, {@code relatedEntityId}) and the additional response fields are all
 * additive — a client written against the previous contract is unaffected. Enum-typed parameters are
 * bound by Spring, so an invalid value returns {@code 400 VALIDATION_ERROR} through the existing
 * {@code MethodArgumentTypeMismatchException} handler rather than reaching the repository.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "System audit trail (ADMIN only)")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/v1/audit-logs")
    @Operation(summary = "List audit log entries (ADMIN only)")
    public ResponseEntity<ApiResponse<PageResult<AuditLogResponse>>> list(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSource source,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String relatedEntityType,
            @RequestParam(required = false) String relatedEntityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        AuditLogSearchCriteria criteria = AuditLogSearchCriteria.builder()
                .actorUserId(actorUserId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action == null ? null : action.name())
                .outcome(outcome == null ? null : outcome.name())
                .source(source == null ? null : source.name())
                .companyId(companyId)
                .plantId(plantId)
                .warehouseId(warehouseId)
                .traceId(traceId)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .from(from)
                .to(to)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(auditLogQueryService.list(
                criteria, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/audit-logs/{auditLogId}")
    @Operation(summary = "Get an audit log entry with its entity targets and field-level changes (ADMIN only)")
    public ResponseEntity<ApiResponse<AuditLogDetailResponse>> get(@PathVariable UUID auditLogId) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogQueryService.get(auditLogId)));
    }
}
