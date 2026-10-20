package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialReturn;
import com.erp.manufacturing.module.workorder.domain.MaterialReturnLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnLineRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnLineResponse;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnPostRequest;
import com.erp.manufacturing.module.workorder.dto.materialreturn.MaterialReturnResponse;
import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnRepository;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G8d (FE R16, R17, D3): unused issued material goes back to the exact warehouse and lot it was issued from, as a
 * {@code RECEIVE} stock movement referencing the return. A return never reduces the gross issued quantity of the
 * component (FE Q11) and never restores a reservation (FE R17). Its cap per issue line is
 * {@code issued − consumed − returned}; when the work order's consumption is unknown (reported before G8c) nothing is
 * returnable until there is evidence (FE Q22).
 */
@Service
@RequiredArgsConstructor
public class MaterialReturnService {

    /** {@code reference_type} of the stock movement a return line posts. */
    static final String MATERIAL_RETURN_REFERENCE_TYPE = "MATERIAL_RETURN";

    private final MaterialReturnRepository returnRepository;
    private final MaterialReturnLineRepository returnLineRepository;
    private final MaterialIssueLineRepository issueLineRepository;
    private final MaterialSourceLedger sourceLedger;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderExecutionSupport support;
    private final IdempotencySupport idempotency;
    private final TraceIdProvider traceIdProvider;
    private final UserLookupService userLookupService;
    private final SecurityAuditorAware auditorAware;

    /** The fingerprint of a return: the work order is part of it, so one key cannot replay onto another work order. */
    private record ReturnCommand(UUID workOrderId, MaterialReturnPostRequest request) {
    }

    /**
     * Posts a return. Every line is checked before anything is written (C9); the document, its stock movements, the
     * WIP ledger row and the work order version move together or not at all. Replaying the same
     * {@code Idempotency-Key} with the same body returns the first document.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RETURN_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_RETURN_POSTED, entityType = "MaterialReturn",
            entityIdExpression = "materialReturnId.toString()")
    public MaterialReturnResponse post(UUID workOrderId, MaterialReturnPostRequest request, String idempotencyKey) {
        String key = idempotency.normalizeKey(idempotencyKey);
        ReturnCommand command = new ReturnCommand(workOrderId, request);
        var existing = returnRepository.findWithWorkOrderByIdempotencyKey(key);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            return toResponse(existing.get());
        }
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS && workOrder.getStatus() != WorkOrderStatus.COMPLETED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Material can be returned only while the work order is IN_PROGRESS or COMPLETED");
        }
        if (!workOrder.isOperationTracking()) {
            throw ExceptionFactory.custom(BusinessErrorCode.PRODUCTION_INPUT_UNKNOWN,
                    "This work order was reported before material consumption was recorded; what is unused is unknown");
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.APPROVAL_REASON_REQUIRED, Map.of("reason", "is required"));
        }
        List<Line> lines = validateLines(workOrder, request.lines());

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // Touched once, before the first flush: the return's own saveAndFlush writes the dirty work order with it, so
        // the version moves exactly once (the G8b lesson, WorkOrderExecutionSupport.flushTouched).
        workOrder.touch();
        MaterialReturn materialReturn = returnRepository.saveAndFlush(MaterialReturn.builder()
                .workOrder(workOrder)
                .reason(reason)
                .postedAt(now)
                .postedBy(auditorAware.getCurrentAuditor().orElse(null))
                .traceId(traceIdProvider.currentTraceId())
                .idempotencyKey(key)
                .payloadHash(idempotency.payloadHash(command))
                .build());
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (Line line : lines) {
            lineNo++;
            MaterialIssueLine source = line.source();
            StockMovement movement = movementService.receive(new InventoryReceiveCommand(
                            source.getItem().getItemId(),
                            source.getWarehouse().getWarehouseId(),
                            source.getLot() == null ? null : source.getLot().getLotId(),
                            null,
                            line.quantity(),
                            reason,
                            MATERIAL_RETURN_REFERENCE_TYPE,
                            materialReturn.getMaterialReturnId().toString(),
                            null,
                            null,
                            null),
                    idempotency.childKey("MR:" + key, lineNo),
                    LotStatus.AVAILABLE).movement();
            materialReturn.getLines().add(MaterialReturnLine.builder()
                    .materialReturn(materialReturn)
                    .lineNo(lineNo)
                    .sourceIssueLine(source)
                    .quantity(line.quantity())
                    .stockMovement(movement)
                    .build());
            total = total.add(line.quantity());
        }
        returnRepository.saveAndFlush(materialReturn);
        wipTransactionService.recordMaterialReturned(workOrder, total, materialReturn.getMaterialReturnId());
        support.flushTouched(workOrder);
        return toResponse(materialReturn);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasMaterialReturnAccess(authentication, "
            + "{'PERM_MATERIAL_RETURN_READ', 'PERM_MATERIAL_RETURN_MANAGE'}, #materialReturnId)")
    public MaterialReturnResponse get(UUID materialReturnId) {
        return toResponse(returnRepository.findWithWorkOrderByMaterialReturnId(materialReturnId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material return", materialReturnId)));
    }

    /**
     * Plant-wide return history. {@code search} matches the return code or the work order number; {@code fromDate} /
     * {@code toDate} are plant-local days on {@code postedAt}, both inclusive.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_RETURN_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_RETURN_MANAGE', 'PLANT', #plantId)")
    public PageResult<MaterialReturnResponse> listByPlant(UUID plantId, UUID workOrderId, String search,
                                                         LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("toDate", "must not be before fromDate"));
        }
        ZoneId zone = (fromDate == null && toDate == null) ? null : support.plantZone(plantId);
        Page<MaterialReturn> page = returnRepository.findByPlant(plantId, workOrderId, support.trimToNull(search),
                fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant(),
                pageable);
        Map<UUID, List<MaterialReturnLineResponse>> lines = lineResponses(page.getContent().stream()
                .map(MaterialReturn::getMaterialReturnId).toList());
        Map<UUID, String> usernames = usernames(page.getContent());
        return PageResult.from(page.map(materialReturn -> toResponse(materialReturn, lines, usernames)));
    }

    /**
     * The issue lines of the work order that still hold unused material (FE §3.3), each with its cap and whether its
     * source can take it back now. A work order whose consumption is unknown lists every line with null quantities.
     * Bounded by the work order's own issue lines, so the page is cut in memory.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_RETURN_MANAGE', #workOrderId)")
    public PageResult<WipMaterialLineResponse> candidates(UUID workOrderId, Pageable pageable) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        List<WipMaterialLineResponse> rows = sourceLedger.lines(workOrder).stream()
                .filter(row -> row.unusedQuantity() == null || row.unusedQuantity().signum() > 0)
                .toList();
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }

    /** One validated line: the source issue line and the quantity going back to it (duplicates added up). */
    private record Line(MaterialIssueLine source, BigDecimal quantity) {
    }

    /**
     * Each source must be a posted issue line of this work order, able to take material back now, and the total asked
     * for it (entries for the same line added up first) at most {@code issued − consumed − returned}. Errors name the
     * first entry of the offending line.
     */
    private List<Line> validateLines(WorkOrder workOrder, List<MaterialReturnLineRequest> entries) {
        Map<UUID, BigDecimal> requested = new LinkedHashMap<>();
        Map<UUID, Integer> firstIndex = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            MaterialReturnLineRequest entry = entries.get(i);
            requested.merge(entry.sourceIssueLineId(), entry.quantity(), BigDecimal::add);
            firstIndex.putIfAbsent(entry.sourceIssueLineId(), i);
        }
        Map<UUID, MaterialIssueLine> posted = issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId()).stream()
                .collect(Collectors.toMap(MaterialIssueLine::getIssueLineId, Function.identity()));
        Map<UUID, BigDecimal> consumed = sourceLedger.consumed(requested.keySet());
        Map<UUID, BigDecimal> returned = sourceLedger.returned(requested.keySet());
        Instant asOf = Instant.now();
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : requested.entrySet()) {
            String prefix = "lines[" + firstIndex.get(entry.getKey()) + "].";
            MaterialIssueLine source = posted.get(entry.getKey());
            if (source == null) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        Map.of(prefix + "sourceIssueLineId", "must be a posted issue line of this work order"));
            }
            String ineligible = MaterialSourceLedger.ineligibleReason(source, asOf);
            if (ineligible != null) {
                throw ineligible(prefix + "sourceIssueLineId", ineligible);
            }
            BigDecimal unused = source.getQuantity()
                    .subtract(consumed.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .subtract(returned.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (entry.getValue().compareTo(unused) > 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, Map.of(prefix + "quantity",
                        "only " + unused.toPlainString() + " of this issue line is unused"));
            }
            lines.add(new Line(source, entry.getValue()));
        }
        return lines;
    }

    /** The source cannot take the material back: the code says how to resolve it (FE A31 — never a generic receipt). */
    private static RuntimeException ineligible(String field, String reason) {
        String message = "the source cannot take material back now: " + reason;
        return switch (reason) {
            case "ITEM_INACTIVE", "WAREHOUSE_INACTIVE" ->
                    ExceptionFactory.withErrors(BusinessErrorCode.RESOURCE_INACTIVE, Map.of(field, message));
            case "SERIAL_TRACKED" ->
                    ExceptionFactory.withErrors(BusinessErrorCode.OPERATION_NOT_ALLOWED, Map.of(field,
                            "serial-tracked material is not returned through a material return"));
            default -> ExceptionFactory.withErrors(BusinessErrorCode.LOT_NOT_ELIGIBLE, Map.of(field, message));
        };
    }

    private MaterialReturnResponse toResponse(MaterialReturn materialReturn) {
        return toResponse(materialReturn, lineResponses(List.of(materialReturn.getMaterialReturnId())),
                usernames(List.of(materialReturn)));
    }

    private static MaterialReturnResponse toResponse(MaterialReturn materialReturn,
                                                     Map<UUID, List<MaterialReturnLineResponse>> lines,
                                                     Map<UUID, String> usernames) {
        WorkOrder workOrder = materialReturn.getWorkOrder();
        UUID postedBy = materialReturn.getPostedBy();
        return new MaterialReturnResponse(
                materialReturn.getMaterialReturnId(),
                materialReturn.getCode(),
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                materialReturn.getStatus(),
                materialReturn.getReason(),
                materialReturn.getPostedAt(),
                postedBy,
                postedBy == null ? null : usernames.get(postedBy),
                materialReturn.getTraceId(),
                workOrder.getVersion(),
                lines.getOrDefault(materialReturn.getMaterialReturnId(), List.of()));
    }

    /** The lines of every return on the page in one query (C14). */
    private Map<UUID, List<MaterialReturnLineResponse>> lineResponses(List<UUID> returnIds) {
        if (returnIds.isEmpty()) {
            return Map.of();
        }
        return returnLineRepository.findWithSourceByMaterialReturnIds(returnIds).stream()
                .collect(Collectors.groupingBy(line -> line.getMaterialReturn().getMaterialReturnId(), LinkedHashMap::new,
                        Collectors.mapping(MaterialReturnService::lineResponse, Collectors.toList())));
    }

    private static MaterialReturnLineResponse lineResponse(MaterialReturnLine line) {
        MaterialIssueLine source = line.getSourceIssueLine();
        return new MaterialReturnLineResponse(
                line.getMaterialReturnLineId(),
                line.getLineNo(),
                source.getIssueLineId(),
                source.getIssue().getIssueId(),
                source.getIssue().getCode(),
                source.getComponentLine().getComponentLineId(),
                source.getItem().getItemId(),
                source.getItem().getCode(),
                source.getItem().getUnit(),
                source.getWarehouse().getWarehouseId(),
                source.getWarehouse().getCode(),
                source.getLot() == null ? null : source.getLot().getLotId(),
                source.getLot() == null ? null : source.getLot().getLotCode(),
                line.getQuantity(),
                line.getStockMovement().getMovementId());
    }

    private Map<UUID, String> usernames(List<MaterialReturn> returns) {
        return userLookupService.findUsernames(returns.stream()
                .map(MaterialReturn::getPostedBy)
                .filter(Objects::nonNull)
                .toList());
    }
}
