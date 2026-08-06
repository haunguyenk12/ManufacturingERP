package com.erp.manufacturing.common.audit.controller;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogQueryService;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
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
 * Audit Logs read API (C2-1, {@code BACKEND_CAPSTONE2_API_GAPS.md §3.3}) — đợt 1: reads the event
 * data {@code audit_logs} already has. {@code changes[]} on the detail endpoint is genuinely empty
 * today (field-level diff capture is đợt 2, out of scope here), not a stub.
 *
 * <p>{@code PERM_AUDIT_READ} is ADMIN-only, checked as a global permission
 * ({@code @permissionGuard.hasPermission}, not {@code hasResourceAccess}) — audit trail is not owned
 * by a single company/plant, so there is no {@code X-Plant-Id} cross-check on the optional
 * {@code plantId} filter here (error-handling.md §5.6.1's cross-check exists to keep a plant-scoped
 * caller's session and URL consistent; that reasoning doesn't apply to a global admin-only read).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "System audit trail (ADMIN only)")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/api/v1/audit-logs")
    @Operation(summary = "List audit log entries (ADMIN only)")
    public ResponseEntity<ApiResponse<PageResult<AuditLogResponse>>> list(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID plantId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogQueryService.list(
                actorUserId, entityType, entityId, action == null ? null : action.name(),
                plantId, traceId, from, to,
                PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/audit-logs/{auditLogId}")
    @Operation(summary = "Get an audit log entry, including field-level changes if any (ADMIN only)")
    public ResponseEntity<ApiResponse<AuditLogDetailResponse>> get(@PathVariable UUID auditLogId) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogQueryService.get(auditLogId)));
    }
}
