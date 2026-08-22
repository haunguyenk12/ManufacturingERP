package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.planning.domain.MrpRequirementLine;
import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.domain.PlanningMessageCode;
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
import java.util.List;
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
        ensureMakeDecision(suggestion);
        suggestion.approve(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(supplySuggestionRepository.save(suggestion));
    }

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasSuggestionAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', #suggestionId)")
    @Auditable(action = AuditAction.SUPPLY_SUGGESTION_REJECTED, entityType = "SupplySuggestion", entityIdExpression = "supplySuggestionId.toString()")
    public SupplySuggestionResponse reject(UUID suggestionId, SupplySuggestionDecisionRequest request) {
        SupplySuggestion suggestion = findSuggestion(suggestionId);
        ensureDraft(suggestion, "Only DRAFT supply suggestions can be rejected");
        ensureMakeDecision(suggestion);
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
            // Document status ⇒ 409 (D11, debt #26). Same condition, same code as the purchasing
            // side of this fork: PurchaseRequisitionService moved to STATE_CONFLICT in D7, and the
            // two convert endpoints must not disagree about one business rule.
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only APPROVED supply suggestions can be converted");
        }
        // Deliberately still 422: the wrong SupplySuggestionType is bad input, not a state the
        // document can leave (§5.3 row 3). Do not "synchronise" this one with the check above.
        if (suggestion.getSuggestionType() != SupplySuggestionType.WORK_ORDER) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only WORK_ORDER suggestions can be converted to work orders");
        }
        ensureNotBlocked(suggestion);

        UUID outputWarehouseId = request != null && request.outputWarehouseId() != null
                ? request.outputWarehouseId()
                : suggestion.getOutputWarehouse() != null
                    ? suggestion.getOutputWarehouse().getWarehouseId()
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
                suggestion.getPlant().getPlantId(),
                createRequest,
                resolveSalesOrderLineId(suggestion),
                // Spec §3.3 "Nguồn gốc": this is the only place a work order acquires planning
                // lineage, because it is the only place one is created out of a proposal.
                new WorkOrderService.PlanningLineage(
                        suggestion.getMrpRun().getMrpRunId(),
                        suggestion.getMrpRun().getCode(),
                        suggestion.getSupplySuggestionId()));

        suggestion.markConverted(WORK_ORDER_REFERENCE_TYPE, workOrder.workOrderId());
        return mapper.toResponse(supplySuggestionRepository.save(suggestion));
    }

    /**
     * Walks the lineage a MAKE proposal was derived from back to the sales order line that asked for
     * it: suggestion → requirement line → planning demand → {@code SALES_ORDER_LINE} reference
     * (spec §2.4). Returns {@code null} whenever that chain does not lead to a sales order line —
     * this module knows the lineage, so it is the one that resolves it (rule {@code C7}).
     *
     * <p>Three cases deliberately produce no allocation rather than an error:
     * <ul>
     *   <li><b>Component levels.</b> {@code expandChildren} copies the parent's
     *       {@code sourceDemand} down the BOM, so a sub-assembly proposal <em>looks</em> like it
     *       serves the sales order. Only the level-0 work order actually makes what the customer
     *       ordered; allocating a sub-assembly too would fulfil the line twice.</li>
     *   <li><b>{@code MANUAL}/{@code FORECAST} demand.</b> There is no customer to fulfil.</li>
     *   <li><b>A reference that will not parse.</b> {@code referenceId} is a free-form
     *       {@code VARCHAR(120)}; refusing to convert over stale planning data would block a work
     *       order that is otherwise perfectly valid.</li>
     * </ul>
     */
    private UUID resolveSalesOrderLineId(SupplySuggestion suggestion) {
        MrpRequirementLine requirementLine = suggestion.getRequirementLine();
        if (requirementLine == null || !Integer.valueOf(0).equals(requirementLine.getRequirementLevel())) {
            return null;
        }
        PlanningDemand demand = requirementLine.getSourceDemand();
        if (demand == null
                || !PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE.equals(demand.getReferenceType())
                || demand.getReferenceId() == null) {
            return null;
        }
        try {
            return UUID.fromString(demand.getReferenceId());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private SupplySuggestion findSuggestion(UUID suggestionId) {
        return supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Supply suggestion", suggestionId));
    }

    /**
     * A {@code BLOCKED} proposal names the missing master data in its messages; re-throwing that as
     * the matching error code keeps the planning screen and the convert response on one vocabulary
     * (B58). Without this the failure would still happen, but only deeper inside
     * {@code WorkOrderService.createFromMrp} and only for the routing case.
     */
    private void ensureNotBlocked(SupplySuggestion suggestion) {
        if (!suggestion.isBlocked()) {
            return;
        }
        List<String> messages = suggestion.messages();
        BusinessErrorCode errorCode = messages.contains(PlanningMessageCode.MISSING_BOM.name())
                ? BusinessErrorCode.MISSING_BOM
                : messages.contains(PlanningMessageCode.MISSING_ROUTING.name())
                    ? BusinessErrorCode.MISSING_ROUTING
                    : messages.contains(PlanningMessageCode.AMBIGUOUS_WAREHOUSE_POLICY.name())
                        ? BusinessErrorCode.AMBIGUOUS_WAREHOUSE_POLICY
                        : BusinessErrorCode.MISSING_WAREHOUSE_POLICY;
        throw ExceptionFactory.businessRule(errorCode,
                "Supply suggestion is BLOCKED: " + String.join(", ", messages));
    }

    /** Document status ⇒ {@code STATE_CONFLICT} (409) per §5.3 (D11, debt #26). */
    private void ensureDraft(SupplySuggestion suggestion, String message) {
        if (!suggestion.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, message);
        }
    }

    private void ensureMakeDecision(SupplySuggestion suggestion) {
        if (suggestion.getSuggestionType() == SupplySuggestionType.PURCHASE_REQUISITION) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "BUY suggestions are read-only; purchasing conversion and stock side effects are deferred");
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
