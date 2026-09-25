package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.dto.AuditLogChangeResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogDetailResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogEntityResponse;
import com.erp.manufacturing.common.audit.dto.AuditLogResponse;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of the audit log. Kept separate from {@link AuditLogService}, which is a write-only
 * facade — the same read/write split the codebase already uses for {@code TokenStoreService} versus
 * {@code AuthService}.
 *
 * <p>Access stays ADMIN-only ({@code PERM_AUDIT_READ}), checked as a global permission rather than a
 * resource-scoped one: the trail is not owned by any single company or plant.
 */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    /**
     * Sort keys a client may name.
     *
     * <p>An allowlist rather than a pass-through: an unknown property reaches Hibernate as an invalid
     * path and surfaces as a 500 from deep inside the repository, so a typo in a query string looks
     * like a server fault. It also stops {@code sortBy} from being used to probe the entity model.
     * The mapped column is what the index is on, which is why {@code occurredAt} maps to a coalesce —
     * historical rows have no {@code occurred_at} and would otherwise sort as if they were the oldest
     * events in the system.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "occurredAt", "occurredAt",
            "createdAt", "createdAt",
            "action", "action",
            "username", "username",
            "outcome", "status");

    private final AuditLogRepository auditLogRepository;
    private final AuditLogChangeRepository auditLogChangeRepository;
    private final AuditLogEntityRowRepository auditLogEntityRowRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_AUDIT_READ')")
    public PageResult<AuditLogResponse> list(AuditLogSearchCriteria criteria, Pageable pageable) {
        criteria.validate();
        return PageResult.from(auditLogRepository
                .searchAdvanced(criteria.actorUserId(), criteria.entityType(), criteria.entityId(),
                        criteria.action(), criteria.outcome(), criteria.source(),
                        criteria.companyId(), criteria.plantId(), criteria.warehouseId(),
                        criteria.traceId(), criteria.from(), criteria.to(),
                        criteria.relatedEntityType(), criteria.relatedEntityId(),
                        withStableOrder(pageable))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_AUDIT_READ')")
    public AuditLogDetailResponse get(UUID auditLogId) {
        AuditLog auditLog = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Audit log", auditLogId));

        List<AuditLogEntityResponse> entities = auditLogEntityRowRepository
                .findByAuditIdOrderByRelationAscEntityTypeAsc(auditLogId).stream()
                .map(row -> new AuditLogEntityResponse(row.getRelation().name(), row.getEntityType(),
                        row.getEntityId(), row.getEntityName()))
                .toList();

        List<AuditLogChangeResponse> changes = auditLogChangeRepository
                .findByAuditIdOrderByCreatedAtAsc(auditLogId).stream()
                .map(this::toChangeResponse)
                .toList();

        return new AuditLogDetailResponse(
                auditLog.getAuditId(), auditLog.getUserId(), auditLog.getUsername(),
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId(),
                auditLog.getEntityName(), auditLog.getDescription(), auditLog.getStatus(),
                auditLog.getReasonCode(), auditLog.getSource(), auditLog.getClientIp(),
                auditLog.getUserAgent(), auditLog.getTraceId(), auditLog.getHttpMethod(),
                auditLog.getRequestPath(), auditLog.getCompanyId(), auditLog.getPlantId(),
                auditLog.getWarehouseId(), auditLog.getMetadata(),
                occurredAt(auditLog), auditLog.getStatus(), auditLog.getCreatedAt(),
                entities, changes);
    }

    /**
     * Adds {@code auditId} as a final sort key.
     *
     * <p>Timestamps collide: a single request can produce several events within the same millisecond,
     * and Postgres is free to return equal-key rows in any order between two queries. Without a
     * unique tie-breaker, paging through the trail can show one event twice and never show another —
     * the failure mode where an investigator concludes something is missing when it is only on a page
     * they were never shown.
     */
    private Pageable withStableOrder(Pageable pageable) {
        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String mapped = SORTABLE.get(order.getProperty());
            if (mapped == null) {
                throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                        "Cannot sort audit logs by '" + order.getProperty() + "'; allowed: "
                                + String.join(", ", SORTABLE.keySet().stream().sorted().toList()));
            }
            sort = sort.and(Sort.by(order.getDirection(), mapped));
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                sort.and(Sort.by(Sort.Direction.DESC, "auditId")));
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getAuditId(), auditLog.getUserId(), auditLog.getUsername(),
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId(),
                auditLog.getEntityName(), auditLog.getDescription(), auditLog.getStatus(),
                auditLog.getReasonCode(), auditLog.getSource(), auditLog.getClientIp(),
                auditLog.getUserAgent(), auditLog.getTraceId(), auditLog.getHttpMethod(),
                auditLog.getRequestPath(), auditLog.getCompanyId(), auditLog.getPlantId(),
                auditLog.getWarehouseId(), occurredAt(auditLog), auditLog.getStatus(),
                auditLog.getCreatedAt());
    }

    /**
     * Historical rows have no {@code occurred_at}. Reporting {@code null} there would make every
     * pre-refactor event look undated, so the materialisation time stands in — the closest true
     * statement available about when it happened.
     */
    private Instant occurredAt(AuditLog auditLog) {
        return auditLog.getOccurredAt() != null ? auditLog.getOccurredAt() : auditLog.getCreatedAt();
    }

    private AuditLogChangeResponse toChangeResponse(AuditLogChange change) {
        return new AuditLogChangeResponse(
                change.getChangeId(), change.getFieldName(), textValue(change.getOldValue()),
                textValue(change.getNewValue()), change.getChangeType().name(), change.getCreatedAt());
    }

    /**
     * Renders one stored {@code jsonb} snapshot as the single wire type the contract promises:
     * {@code String} or {@code null}, never an object, a number or a boolean.
     *
     * <ul>
     *   <li>SQL {@code NULL} and JSON {@code null} both become {@code null}.</li>
     *   <li>A text snapshot loses its JSON quoting, so {@code "\"DRAFT\""} reaches the client as
     *       {@code DRAFT} — the value a user reads, not a quoted JSON literal.</li>
     *   <li>Numbers and booleans become their plain string form ({@code 10}, {@code true}).</li>
     *   <li>Objects and arrays become compact JSON text — byte-for-byte what was stored, so a client
     *       that wants the structure back only needs one {@code JSON.parse}.</li>
     *   <li>A row that is not parseable JSON at all (only reachable for hand-written data) is passed
     *       through unchanged instead of being dropped.</li>
     * </ul>
     */
    private String textValue(String value) {
        if (value == null) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(value);
        } catch (Exception ignored) {
            return value;
        }
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
