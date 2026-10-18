package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.planning.domain.MrpRequirementLine;
import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.MrpRunStatus;
import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionConversion;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionStatus;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionType;
import com.erp.manufacturing.module.planning.dto.PlanningConversionDocumentResponse;
import com.erp.manufacturing.module.planning.dto.PlanningConversionEntryRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionRequest;
import com.erp.manufacturing.module.planning.dto.PlanningConversionResponse;
import com.erp.manufacturing.module.planning.dto.PlanningConversionResult;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.MrpRequirementLineRepository;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.purchasing.dto.PlanningPurchaseLine;
import com.erp.manufacturing.module.purchasing.dto.PurchaseOrderResponse;
import com.erp.manufacturing.module.purchasing.service.PurchaseOrderService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSource;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderCreateRequest;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderResponse;
import com.erp.manufacturing.module.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts the proposals of a commercial run into draft documents, partially and repeatedly (G6b,
 * D5/D9/D10): MAKE ⇒ one {@code DRAFT} work order per entry through the canonical
 * {@link WorkOrderService#createFromMrp}; BUY ⇒ one {@code DRAFT} purchase order for all entries.
 * No approval step is required on this path (D10); the legacy {@code convert-to-*} endpoints keep
 * their {@code APPROVED} gate (B74).
 *
 * <p>Everything happens in one transaction under a lock on the run row (B180): the conversion
 * version, the source version, the remaining quantities and the sales order line's uncovered
 * quantity are all read under it, then documents and ledger rows are written. A failure anywhere —
 * the last purchase order line included — rolls back every document of the request.
 */
@Service
@RequiredArgsConstructor
public class PlanningConversionService {

    private static final String WORK_ORDER_REFERENCE_TYPE = "WORK_ORDER";
    private static final String PURCHASE_ORDER_REFERENCE_TYPE = "PURCHASE_ORDER";

    private final MrpRunRepository mrpRunRepository;
    private final MrpRequirementLineRepository requirementLineRepository;
    private final SupplySuggestionRepository supplySuggestionRepository;
    private final SupplySuggestionConversionRepository conversionRepository;
    private final PlanningSourceVersionCalculator sourceVersionCalculator;
    private final SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    private final WorkOrderService workOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final MrpPlanningMapper mapper;
    private final IdempotencySupport idempotency;

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_SUPPLY_SUGGESTION_MANAGE', #runId)")
    @Auditable(action = AuditAction.MRP_RUN_CONVERSION_CREATED, entityType = "MrpRun",
               entityIdExpression = "mrpRunId.toString()")
    public PlanningConversionResult convert(UUID runId, PlanningConversionRequest request, String idempotencyKey) {
        String key = StringUtils.hasText(idempotencyKey) ? idempotency.normalizeKey(idempotencyKey) : null;
        MrpRun run = mrpRunRepository.lockByMrpRunId(runId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", runId));
        if (key != null) {
            List<SupplySuggestionConversion> replayed =
                    conversionRepository.findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(runId, key);
            if (!replayed.isEmpty()) {
                idempotency.ensureSamePayload(replayed.get(0).getPayloadHash(), request);
                return replay(run, replayed);
            }
        }
        if (!run.isCommercial() || run.getStatus() != MrpRunStatus.COMPLETED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a COMPLETED commercial run can be converted here; use the supply suggestion endpoints");
        }
        if (!request.expectedConversionVersion().equals(run.getConversionVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Run conversion version is " + run.getConversionVersion() + ", not "
                            + request.expectedConversionVersion() + "; reload the run");
        }
        if (!request.expectedSourceVersion().equals(run.getSourceVersion())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNING_SOURCE_STALE,
                    Map.of("expectedSourceVersion", "does not match this run's source version"));
        }
        // Q23: checked inside the converting transaction, under the run lock — no TOCTOU window.
        if (!run.getSourceVersion().equals(sourceVersionCalculator.computeCurrent(run))) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNING_SOURCE_STALE,
                    Map.of("expectedSourceVersion", "the sources of this run changed; run planning again"));
        }

        List<PlanningConversionEntryRequest> entries = request.entries();
        List<SupplySuggestion> suggestions = resolveSuggestions(run, entries);
        SupplySuggestionType type = ensureOneType(suggestions);
        if (type == SupplySuggestionType.PURCHASE_REQUISITION && request.supplierId() == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("supplierId", "is required to convert BUY proposals"));
        }
        if (type == SupplySuggestionType.WORK_ORDER && request.supplierId() != null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("supplierId", "must be omitted for MAKE proposals"));
        }
        ensureWithinRemaining(entries, suggestions);

        UUID batchId = UUID.randomUUID();
        int nextVersion = run.getConversionVersion() + 1;
        String payloadHash = key == null ? null : idempotency.payloadHash(request);
        List<SupplySuggestionConversion> rows = new ArrayList<>();
        List<PlanningConversionDocumentResponse> documents = type == SupplySuggestionType.WORK_ORDER
                ? convertToWorkOrders(run, entries, suggestions, rows)
                : convertToPurchaseOrder(run, entries, suggestions, request.supplierId(), rows);
        for (SupplySuggestionConversion row : rows) {
            row.setConversionBatchId(batchId);
            row.setConversionVersion(nextVersion);
            row.setIdempotencyKey(key);
            row.setPayloadHash(payloadHash);
            row.setMrpRunId(runId);
        }
        // A proposal touched by the ledger is closed to the legacy one-shot convert endpoints, which
        // would otherwise convert its full quantity a second time.
        for (int i = 0; i < suggestions.size(); i++) {
            SupplySuggestion suggestion = suggestions.get(i);
            if (suggestion.getStatus() != SupplySuggestionStatus.CONVERTED) {
                suggestion.markConverted(
                        type == SupplySuggestionType.WORK_ORDER ? WORK_ORDER_REFERENCE_TYPE : PURCHASE_ORDER_REFERENCE_TYPE,
                        rows.get(i).getTargetId());
            }
        }
        conversionRepository.saveAll(rows);
        run.advanceConversionVersion();
        mrpRunRepository.saveAndFlush(run);
        return new PlanningConversionResult(runId, run.getConversionVersion(), run.getSourceVersion(), documents,
                rows.stream().map(mapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public PageResult<PlanningConversionResponse> list(UUID runId, Pageable pageable) {
        if (!mrpRunRepository.existsById(runId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", runId);
        }
        return PageResult.from(conversionRepository.findByMrpRunId(runId, pageable).map(mapper::toResponse));
    }

    private List<SupplySuggestion> resolveSuggestions(MrpRun run, List<PlanningConversionEntryRequest> entries) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!seen.add(entries.get(i).requirementLineId())) {
                errors.put("entries[" + i + "].requirementLineId", "is listed more than once");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        Map<UUID, SupplySuggestion> byLine = supplySuggestionRepository.findByRequirementLineIds(seen).stream()
                .collect(Collectors.toMap(s -> s.getRequirementLine().getMrpRequirementLineId(), s -> s, (a, b) -> a));
        List<SupplySuggestion> result = new ArrayList<>();
        for (PlanningConversionEntryRequest entry : entries) {
            SupplySuggestion suggestion = byLine.get(entry.requirementLineId());
            if (suggestion == null || !suggestion.getMrpRun().getMrpRunId().equals(run.getMrpRunId())) {
                boolean lineOfRun = requirementLineRepository
                        .findByMrpRunMrpRunIdAndMrpRequirementLineId(run.getMrpRunId(), entry.requirementLineId())
                        .isPresent();
                if (!lineOfRun) {
                    throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND,
                            "Requirement line", entry.requirementLineId());
                }
                throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                        "Requirement line " + entry.requirementLineId() + " is covered; there is nothing to convert");
            }
            if (suggestion.getStatus() == SupplySuggestionStatus.REJECTED) {
                throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                        "The proposal of requirement line " + entry.requirementLineId() + " was rejected");
            }
            SupplySuggestionService.ensureNotBlocked(suggestion);
            result.add(suggestion);
        }
        return result;
    }

    private SupplySuggestionType ensureOneType(List<SupplySuggestion> suggestions) {
        SupplySuggestionType type = suggestions.get(0).getSuggestionType();
        if (suggestions.stream().anyMatch(s -> s.getSuggestionType() != type)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Convert MAKE and BUY proposals in separate requests");
        }
        return type;
    }

    private void ensureWithinRemaining(List<PlanningConversionEntryRequest> entries, List<SupplySuggestion> suggestions) {
        Map<UUID, BigDecimal> ledger = conversionRepository.sumBySuggestionIds(
                        suggestions.stream().map(SupplySuggestion::getSupplySuggestionId).toList())
                .stream()
                .collect(Collectors.toMap(
                        SupplySuggestionConversionRepository.SuggestionQuantityProjection::getSupplySuggestionId,
                        SupplySuggestionConversionRepository.SuggestionQuantityProjection::getQuantity));
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            SupplySuggestion suggestion = suggestions.get(i);
            BigDecimal remaining = MrpPlanningMapper.remaining(
                    suggestion.getSuggestedQuantity(),
                    MrpPlanningMapper.convertedQuantity(suggestion,
                            ledger.getOrDefault(suggestion.getSupplySuggestionId(), BigDecimal.ZERO)));
            if (entries.get(i).quantity().compareTo(remaining) > 0) {
                errors.put("entries[" + i + "].quantity",
                        "exceeds the remaining convertible quantity " + remaining.stripTrailingZeros().toPlainString());
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PLANNED_QUANTITY_EXCEEDED, errors);
        }
    }

    private List<PlanningConversionDocumentResponse> convertToWorkOrders(MrpRun run,
                                                                         List<PlanningConversionEntryRequest> entries,
                                                                         List<SupplySuggestion> suggestions,
                                                                         List<SupplySuggestionConversion> rows) {
        Map<UUID, UUID> rootLineBySuggestion = new LinkedHashMap<>();
        for (SupplySuggestion suggestion : suggestions) {
            UUID salesOrderLineId = rootSalesOrderLineId(suggestion);
            if (salesOrderLineId != null) {
                rootLineBySuggestion.put(suggestion.getSupplySuggestionId(), salesOrderLineId);
            }
        }
        // B181: a work order covers at most what the line still lacks right now — another run, a
        // hold or an earlier conversion may have covered it since this run was computed.
        Map<UUID, SalesOrderPlanningSource> sources =
                salesOrderPlanningSourceService.findSources(rootLineBySuggestion.values());

        List<PlanningConversionDocumentResponse> documents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            PlanningConversionEntryRequest entry = entries.get(i);
            SupplySuggestion suggestion = suggestions.get(i);
            Warehouse output = suggestion.getOutputWarehouse() != null ? suggestion.getOutputWarehouse() : suggestion.getWarehouse();
            if (output == null) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "The MAKE proposal of requirement line " + entry.requirementLineId() + " has no output warehouse");
            }
            UUID salesOrderLineId = rootLineBySuggestion.get(suggestion.getSupplySuggestionId());
            SalesOrderPlanningSource source = salesOrderLineId == null ? null : sources.get(salesOrderLineId);
            WorkOrderResponse workOrder = workOrderService.createFromMrp(
                    run.getPlant().getPlantId(),
                    new WorkOrderCreateRequest(
                            "MRP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT),
                            suggestion.getItem().getItemId(),
                            output.getWarehouseId(),
                            entry.quantity(),
                            null,
                            suggestion.getNeededByDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                            "Created from MRP run " + run.getCode() + ", requirement " + entry.requirementLineId()),
                    source == null ? null : salesOrderLineId,
                    new WorkOrderService.PlanningLineage(run.getMrpRunId(), run.getCode(), suggestion.getSupplySuggestionId()),
                    source == null ? null : source.uncoveredQuantity());
            rows.add(ledgerRow(suggestion, entry, i + 1, SupplyConversionTargetType.WORK_ORDER,
                    workOrder.workOrderId(), null, workOrder.workOrderNo()));
            documents.add(new PlanningConversionDocumentResponse(
                    SupplyConversionTargetType.WORK_ORDER.name(), workOrder.workOrderId(), workOrder.workOrderNo(),
                    workOrder.status(), entry.quantity(), List.of(entry.requirementLineId())));
        }
        return documents;
    }

    private List<PlanningConversionDocumentResponse> convertToPurchaseOrder(MrpRun run,
                                                                            List<PlanningConversionEntryRequest> entries,
                                                                            List<SupplySuggestion> suggestions,
                                                                            UUID supplierId,
                                                                            List<SupplySuggestionConversion> rows) {
        Set<UUID> receivingWarehouses = new HashSet<>();
        List<PlanningPurchaseLine> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SupplySuggestion suggestion = suggestions.get(i);
            Warehouse receiving = suggestion.getReceivingWarehouse() != null
                    ? suggestion.getReceivingWarehouse() : suggestion.getWarehouse();
            if (receiving == null) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "The BUY proposal of requirement line " + entries.get(i).requirementLineId()
                                + " has no receiving warehouse");
            }
            receivingWarehouses.add(receiving.getWarehouseId());
            lines.add(new PlanningPurchaseLine(suggestion.getItem().getItemId(), entries.get(i).quantity(),
                    suggestion.getNeededByDate()));
        }
        if (receivingWarehouses.size() > 1) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "One purchase order receives into one warehouse; convert each warehouse separately");
        }
        LocalDate today = LocalDate.now(ZoneId.of(run.getPlant().getTimezone()));
        PurchaseOrderResponse order = purchaseOrderService.createFromPlanning(
                run.getCompany().getCompanyId(),
                run.getPlant().getPlantId(),
                receivingWarehouses.iterator().next(),
                supplierId,
                today,
                "Created from MRP run " + run.getCode(),
                lines);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < entries.size(); i++) {
            rows.add(ledgerRow(suggestions.get(i), entries.get(i), i + 1, SupplyConversionTargetType.PURCHASE_ORDER,
                    order.purchaseOrderId(), order.lines().get(i).purchaseOrderLineId(), order.purchaseOrderNo()));
            total = total.add(entries.get(i).quantity());
        }
        return List.of(new PlanningConversionDocumentResponse(
                SupplyConversionTargetType.PURCHASE_ORDER.name(), order.purchaseOrderId(), order.purchaseOrderNo(),
                order.status(), total, entries.stream().map(PlanningConversionEntryRequest::requirementLineId).toList()));
    }

    private SupplySuggestionConversion ledgerRow(SupplySuggestion suggestion,
                                                 PlanningConversionEntryRequest entry,
                                                 int entryNo,
                                                 SupplyConversionTargetType targetType,
                                                 UUID targetId,
                                                 UUID targetLineId,
                                                 String targetCode) {
        return SupplySuggestionConversion.builder()
                .supplySuggestionId(suggestion.getSupplySuggestionId())
                .requirementLineId(entry.requirementLineId())
                .entryNo(entryNo)
                .quantity(entry.quantity())
                .targetType(targetType)
                .targetId(targetId)
                .targetLineId(targetLineId)
                .targetCode(targetCode)
                .build();
    }

    /**
     * Only a level-0 requirement of a sales order demand covers the order (A28): a component's
     * proposal inherits the root demand for trace, but its work order makes a part, not what the
     * customer ordered.
     */
    private UUID rootSalesOrderLineId(SupplySuggestion suggestion) {
        MrpRequirementLine line = suggestion.getRequirementLine();
        if (line == null || !Integer.valueOf(0).equals(line.getRequirementLevel())) {
            return null;
        }
        PlanningDemand demand = line.getSourceDemand();
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

    /**
     * Same documents as the first response. {@code status} is {@code null}: it is whatever the
     * document is now, which the ledger does not track — read the document for it.
     */
    private PlanningConversionResult replay(MrpRun run, List<SupplySuggestionConversion> rows) {
        Map<UUID, List<SupplySuggestionConversion>> byTarget = new LinkedHashMap<>();
        for (SupplySuggestionConversion row : rows) {
            byTarget.computeIfAbsent(row.getTargetId(), id -> new ArrayList<>()).add(row);
        }
        List<PlanningConversionDocumentResponse> documents = byTarget.values().stream()
                .map(group -> new PlanningConversionDocumentResponse(
                        group.get(0).getTargetType().name(),
                        group.get(0).getTargetId(),
                        group.get(0).getTargetCode(),
                        null,
                        group.stream().map(SupplySuggestionConversion::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                        group.stream().map(SupplySuggestionConversion::getRequirementLineId).toList()))
                .toList();
        return new PlanningConversionResult(run.getMrpRunId(), rows.get(0).getConversionVersion(),
                run.getSourceVersion(), documents, rows.stream().map(mapper::toResponse).toList());
    }
}
