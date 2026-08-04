package com.erp.manufacturing.module.uom.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import com.erp.manufacturing.module.uom.dto.UomCreateRequest;
import com.erp.manufacturing.module.uom.dto.UomResponse;
import com.erp.manufacturing.module.uom.dto.UomUpdateRequest;
import com.erp.manufacturing.module.uom.mapper.UomMapper;
import com.erp.manufacturing.module.uom.repository.UomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Unit of measure master data (C2-3, {@code BACKEND_CAPSTONE2_API_GAPS.md §3.1}).
 *
 * <p>Global — every method authorizes with {@code @permissionGuard.hasPermission(...)}, the
 * no-resource overload used by {@code AccessControlService}/{@code OrganizationService} for the same
 * reason: there is no company/plant to scope against (see {@code Uom} javadoc).
 *
 * <p>{@code activate}/{@code deactivate} are idempotent by design — calling {@code activate} on a UOM
 * that is already {@code ACTIVE} is a no-op 200, not an error. Unlike {@code RoutingService.activate}
 * (which deactivates a sibling "previous ACTIVE revision"), a UOM has no revision concept, so there is
 * no state-machine reason to reject an already-satisfied request.
 */
@Service
@RequiredArgsConstructor
public class UomService {

    private final UomRepository uomRepository;
    private final UomMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_MANAGE')")
    @Auditable(action = AuditAction.UOM_CREATED, entityType = "Uom", entityIdExpression = "uomId.toString()")
    public UomResponse create(UomCreateRequest request) {
        String code = normalizeCode(request.code());
        if (uomRepository.existsByCode(code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "UOM code", code);
        }
        Uom uom = Uom.builder()
                .code(code)
                .name(requireText(request.name(), "UOM name"))
                .description(trimToNull(request.description()))
                .build();
        return mapper.toResponse(uomRepository.save(uom));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_READ')")
    public PageResult<UomResponse> list(UomStatus status, String keyword, Pageable pageable) {
        return PageResult.from(uomRepository.search(status, trimToNull(keyword), pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_READ')")
    public UomResponse get(UUID uomId) {
        return mapper.toResponse(findUom(uomId));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_MANAGE')")
    @Auditable(action = AuditAction.UOM_UPDATED, entityType = "Uom", entityIdExpression = "uomId.toString()")
    public UomResponse update(UUID uomId, UomUpdateRequest request) {
        Uom uom = findUom(uomId);
        if (request.name() != null) {
            uom.setName(requireText(request.name(), "UOM name"));
        }
        if (request.description() != null) {
            uom.setDescription(trimToNull(request.description()));
        }
        return mapper.toResponse(uomRepository.save(uom));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_MANAGE')")
    @Auditable(action = AuditAction.UOM_ACTIVATED, entityType = "Uom", entityIdExpression = "uomId.toString()")
    public UomResponse activate(UUID uomId) {
        Uom uom = findUom(uomId);
        uom.activate();
        return mapper.toResponse(uomRepository.save(uom));
    }

    /**
     * "Không delete/deactivate nếu làm hỏng Item/BOM/transaction đang tham chiếu" (spec §3.1) is
     * intentionally NOT enforced with a reference count here: nothing in this schema has an FK to
     * {@code uoms} yet — {@code items.unit} is still a free {@code String} (NEXT_PHASE_PLAN.md C2-3,
     * "KHÔNG làm gì"). Deactivating a UOM today can never conflict with anything. When a future phase
     * adds {@code items.uom_id}, this method is where the reference check belongs — do not add a
     * check now that would just always pass and read as tested when it is not.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_UOM_MANAGE')")
    @Auditable(action = AuditAction.UOM_DEACTIVATED, entityType = "Uom", entityIdExpression = "uomId.toString()")
    public UomResponse deactivate(UUID uomId) {
        Uom uom = findUom(uomId);
        uom.deactivate();
        return mapper.toResponse(uomRepository.save(uom));
    }

    private Uom findUom(UUID uomId) {
        return uomRepository.findById(uomId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "UOM", uomId));
    }

    private String normalizeCode(String value) {
        return requireText(value, "UOM code").toUpperCase();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
