package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionType;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionConvertWorkOrderRequest;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionDecisionRequest;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplySuggestionService {

    private static final String WORK_ORDER_REFERENCE_TYPE = "WORK_ORDER";

    private final SupplySuggestionRepository supplySuggestionRepository;
    private final WorkOrderService workOrderService;
    private final MrpPlanningMapper mapper;

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasSuggestionAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', #suggestionId)")
    @Auditable(action = AuditAction.SUPPLY_SUGGESTION_APPROVED, entityType = "SupplySuggestion", entityIdExpression = "supplySuggestionId.toString()")
    public SupplySuggestionResponse approve(UUID suggestionId, SupplySuggestionDecisionRequest request) {
        SupplySuggestion suggestion = findSuggestion(suggestionId);
        ensureDraft(suggestion, "Only DRAFT supply suggestions can be approved");
        suggestion.approve(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(supplySuggestionRepository.save(suggestion));
    }

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasSuggestionAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', #suggestionId)")
    @Auditable(action = AuditAction.SUPPLY_SUGGESTION_REJECTED, entityType = "SupplySuggestion", entityIdExpression = "supplySuggestionId.toString()")
    public SupplySuggestionResponse reject(UUID suggestionId, SupplySuggestionDecisionRequest request) {
        SupplySuggestion suggestion = findSuggestion(suggestionId);
        ensureDraft(suggestion, "Only DRAFT supply suggestions can be rejected");
        suggestion.reject(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(supplySuggestionRepository.save(suggestion));
    }

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasSuggestionAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', #suggestionId)")
    @Auditable(action = AuditAction.SUPPLY_SUGGESTION_CONVERTED, entityType = "SupplySuggestion", entityIdExpression = "supplySuggestionId.toString()")
    public SupplySuggestionResponse convertToWorkOrder(UUID suggestionId,
                                                       SupplySuggestionConvertWorkOrderRequest request) {
        SupplySuggestion suggestion = findSuggestion(suggestionId);
        if (!suggestion.isApproved()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only APPROVED supply suggestions can be converted");
        }
        if (suggestion.getSuggestionType() != SupplySuggestionType.WORK_ORDER) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only WORK_ORDER suggestions can be converted to work orders");
        }

        UUID outputWarehouseId = request != null && request.outputWarehouseId() != null
                ? request.outputWarehouseId()
                : suggestion.getWarehouse() == null ? null : suggestion.getWarehouse().getWarehouseId();
        if (outputWarehouseId == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Output warehouse is required when suggestion is not warehouse-specific");
        }

        WorkOrderCreateRequest createRequest = new WorkOrderCreateRequest(
                resolveWorkOrderNo(suggestion, request),
                suggestion.getItem().getItemId(),
                outputWarehouseId,
                suggestion.getSuggestedQuantity(),
                request == null ? null : request.plannedStartAt(),
                request != null && request.plannedEndAt() != null
                        ? request.plannedEndAt()
                        : suggestion.getNeededByDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                resolveNotes(suggestion, request));
        WorkOrderResponse workOrder = workOrderService.createFromMrp(
                suggestion.getPlant().getPlantId(), createRequest);

        suggestion.markConverted(WORK_ORDER_REFERENCE_TYPE, workOrder.workOrderId());
        return mapper.toResponse(supplySuggestionRepository.save(suggestion));
    }

    private SupplySuggestion findSuggestion(UUID suggestionId) {
        return supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Supply suggestion", suggestionId));
    }

    private void ensureDraft(SupplySuggestion suggestion, String message) {
        if (!suggestion.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED, message);
        }
    }

    private String resolveWorkOrderNo(SupplySuggestion suggestion, SupplySuggestionConvertWorkOrderRequest request) {
        if (request != null && StringUtils.hasText(request.workOrderNo())) {
            return request.workOrderNo().trim();
        }
        return "MRP-" + suggestion.getSupplySuggestionId()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private String resolveNotes(SupplySuggestion suggestion, SupplySuggestionConvertWorkOrderRequest request) {
        String note = request == null ? null : trimToNull(request.notes());
        String generated = "Created from MRP suggestion " + suggestion.getSupplySuggestionId();
        return note == null ? generated : note + "\n" + generated;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
