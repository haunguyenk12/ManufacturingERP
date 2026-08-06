package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.dto.AuditLogChangeResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Read side of the audit log (C2-1, {@code BACKEND_CAPSTONE2_API_GAPS.md §3.3}). Kept separate from
 * {@link AuditLogService}, which is documented as a write-only, fire-and-forget event-publishing
 * facade — mirrors the existing {@code TokenStoreService}/{@code AuthService} split (infra read/write
 * vs. business-facing service).
 */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogChangeRepository auditLogChangeRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_AUDIT_READ')")
    public PageResult<AuditLogResponse> list(UUID actorUserId, String entityType, String entityId,
                                             String action, UUID plantId, String traceId,
                                             Instant from, Instant to, Pageable pageable) {
        return PageResult.from(auditLogRepository
                .search(actorUserId, entityType, entityId, action, plantId, traceId, from, to, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_AUDIT_READ')")
    public AuditLogDetailResponse get(UUID auditLogId) {
        AuditLog auditLog = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Audit log", auditLogId));

        var changes = auditLogChangeRepository.findByAuditIdOrderByCreatedAtAsc(auditLogId).stream()
                .map(this::toChangeResponse)
                .toList();

        return new AuditLogDetailResponse(
                auditLog.getAuditId(), auditLog.getUserId(), auditLog.getUsername(),
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId(),
                auditLog.getDescription(), auditLog.getStatus(), auditLog.getClientIp(),
                auditLog.getUserAgent(), auditLog.getTraceId(), auditLog.getPlantId(),
                auditLog.getCreatedAt(), changes);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getAuditId(), auditLog.getUserId(), auditLog.getUsername(),
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId(),
                auditLog.getDescription(), auditLog.getStatus(), auditLog.getClientIp(),
                auditLog.getUserAgent(), auditLog.getTraceId(), auditLog.getPlantId(),
                auditLog.getCreatedAt());
    }

    private AuditLogChangeResponse toChangeResponse(AuditLogChange change) {
        return new AuditLogChangeResponse(
                change.getChangeId(), change.getFieldName(), change.getOldValue(),
                change.getNewValue(), change.getChangeType().name(), change.getCreatedAt());
    }
}
