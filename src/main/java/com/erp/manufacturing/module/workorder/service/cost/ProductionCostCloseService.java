package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.delivery.domain.Delivery;
import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.workorder.domain.ProductionCostAdjustment;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRun;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Adjustment;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostCheck;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostSnapshot;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryCost;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryCostLine;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.FinalizeRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.PartAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ReceiptAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunCommandResult;
import com.erp.manufacturing.module.workorder.repository.ProductionCostAdjustmentRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRunRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * G11c: finalize the production cost and close the work order in one transaction (FE C10, R21-R23), the value-only
 * entries it writes (C12) and the effective production cost of a delivery (C11).
 *
 * <p>Order of {@link #finalizeAndClose}: authorisation ({@code @PreAuthorize}) → replay → reason → lock the work order
 * row and re-read it → authorisation again under the lock (a permission revoked while waiting must not commit, FE A39)
 * → versions → run → live re-calculation on the server (never the client's totals or the stored preview, FE A21) →
 * source freshness → the 11 checks → snapshot, value entries, {@code CLOSED}, all or nothing (B259-B262).
 */
@Service
@RequiredArgsConstructor
public class ProductionCostCloseService {

    private static final Set<String> OPERATIONAL = Set.of(ProductionCostCalculator.CHECK_OPERATIONS,
            ProductionCostCalculator.CHECK_ISSUES, ProductionCostCalculator.CHECK_MATERIAL,
            ProductionCostCalculator.CHECK_RESERVATIONS, ProductionCostCalculator.CHECK_RECEIPTS,
            ProductionCostCalculator.CHECK_QC);
    private static final Set<String> COST_DATA = Set.of(ProductionCostCalculator.CHECK_BASELINE,
            ProductionCostCalculator.CHECK_COST);

    private final ProductionCostService costService;
    private final ProductionCostRunRepository runRepository;
    private final ProductionCostAdjustmentRepository adjustmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderIdempotency idempotencyRecords;
    private final PermissionGuard permissionGuard;
    private final SecurityAuditorAware auditorAware;
    private final DeliveryLookupService deliveryLookupService;
    private final EntityManager entityManager;

    @Transactional
    @PreAuthorize(CostAccess.CLOSE_ON_WORK_ORDER)
    @Auditable(action = AuditAction.WORK_ORDER_COST_FINALIZED, entityType = "WorkOrder",
            entityIdExpression = "#workOrderId.toString()")
    public RunCommandResult finalizeAndClose(UUID workOrderId, FinalizeRequest request, String rawKey) {
        String key = idempotencyRecords.requiredKey(rawKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        var replayed = idempotencyRecords.replay(key, WorkOrderCommand.COST_FINALIZE_CLOSE, fingerprint);
        if (replayed.isPresent()) {
            ProductionCostRun run = runRepository.findById(replayed.get()).orElseThrow();
            WorkOrder workOrder = costService.findWorkOrder(workOrderId);
            return new RunCommandResult(workOrderId, run.getRunId(), run.getRunVersion(), workOrder.getVersion());
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("reason", "must not be blank"));
        }

        WorkOrder workOrder = lock(workOrderId);
        ensureStillAuthorised(workOrder.getPlant().getPlantId());
        if (!Objects.equals(workOrder.getVersion(), request.expectedVersion())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.CONCURRENT_MODIFICATION, Map.of("expectedVersion",
                    "work order version is " + workOrder.getVersion() + ", not " + request.expectedVersion()));
        }
        ProductionCostRun run = runRepository.findByRunIdAndWorkOrderId(request.runId(), workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Cost run", request.runId()));
        if (run.isFinalized()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "This cost run is already finalized");
        }
        if (!Objects.equals(run.getRunVersion(), request.expectedRunVersion())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.CONCURRENT_MODIFICATION, Map.of("expectedRunVersion",
                    "run version is " + run.getRunVersion() + ", not " + request.expectedRunVersion()));
        }

        ProductionCostService.Evaluation evaluation = costService.evaluate(workOrder);
        ProductionCostService.ensureFresh(evaluation.sourceVersion(), request.expectedSourceVersion());
        if (!evaluation.sourceVersion().equals(run.getSourceVersion())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.COST_SOURCE_STALE, Map.of("runId",
                    "the run was calculated on other sources; calculate again"));
        }
        // PREVIEW is judged against the run named in the request, not merely the latest one.
        List<CostCheck> checks = new ArrayList<>(evaluation.snapshot().checks().stream()
                .filter(check -> !ProductionCostCalculator.CHECK_PREVIEW.equals(check.code())).toList());
        checks.add(new CostCheck(ProductionCostCalculator.CHECK_PREVIEW, true, List.of(), null));
        ensureChecks(checks);
        idempotencyRecords.record(key, WorkOrderCommand.COST_FINALIZE_CLOSE, run.getRunId(), fingerprint);

        CostSnapshot snapshot = ProductionCostService.withChecks(evaluation.snapshot(), checks);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        adjustmentRepository.saveAll(adjustments(workOrder, run.getRunId(), snapshot, reason));

        run.setState(ProductionCostRun.FINALIZED);
        run.setRunVersion(run.getRunVersion() + 1);
        run.setSnapshot(costService.write(snapshot));
        run.setPlannedTotal(snapshot.planned().total());
        run.setActualTotal(snapshot.actual().total());
        run.setFinalizedAt(now);
        run.setFinalizedBy(actor);
        run.setCloseReason(reason);
        runRepository.save(run);

        workOrder.close(now);
        WorkOrder saved = workOrderRepository.saveAndFlush(workOrder);
        return new RunCommandResult(workOrderId, run.getRunId(), run.getRunVersion(), saved.getVersion());
    }

    /**
     * Row lock first, then re-read: the guard of {@code @PreAuthorize} already loaded the work order into this request's
     * persistence context (open-session-in-view), and a lock on an entity already managed would only compare versions
     * instead of reading the row again (CLAUDE.md §0.58 #1, B194).
     */
    private WorkOrder lock(UUID workOrderId) {
        int locked = entityManager.createNativeQuery(
                        "select 1 from work_orders where work_order_id = :id for update")
                .setParameter("id", workOrderId).getResultList().size();
        if (locked == 0) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId);
        }
        WorkOrder workOrder = costService.findWorkOrder(workOrderId);
        entityManager.refresh(workOrder);
        return workOrder;
    }

    private void ensureStillAuthorised(UUID plantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean close = authentication != null
                && permissionGuard.hasResourceAccess(authentication, "PERM_WORK_ORDER_CLOSE", "PLANT", plantId);
        boolean view = authentication != null && CostAccess.COST_VIEW.stream()
                .anyMatch(code -> permissionGuard.hasResourceAccess(authentication, code, "PLANT", plantId));
        if (!close || !view) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Which code a failed checklist is answered with (FE §4): a wrong status first, then the shop floor's open work,
     * then missing cost data, then an output value that cannot be attributed, then a stale preview.
     */
    static void ensureChecks(List<CostCheck> checks) {
        Map<String, CostCheck> failed = new LinkedHashMap<>();
        checks.stream().filter(check -> !check.ok()).forEach(check -> failed.put(check.code(), check));
        if (failed.isEmpty()) {
            return;
        }
        ErrorCode code;
        if (failed.containsKey(ProductionCostCalculator.CHECK_STATE)) {
            code = BusinessErrorCode.STATE_CONFLICT;
        } else if (failed.keySet().stream().anyMatch(OPERATIONAL::contains)) {
            code = BusinessErrorCode.CLOSE_BLOCKED;
        } else if (failed.keySet().stream().anyMatch(COST_DATA::contains)) {
            code = BusinessErrorCode.COST_DATA_INCOMPLETE;
        } else if (failed.containsKey(ProductionCostCalculator.CHECK_VALUATION)) {
            code = BusinessErrorCode.VALUATION_UNRECONCILED;
        } else {
            code = BusinessErrorCode.COST_SOURCE_STALE;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        failed.values().forEach(check -> errors.put(check.code(), String.join(",", check.reasonCodes())));
        throw ExceptionFactory.withErrors(code, errors);
    }

    /** Value-only entries from the allocations of the final snapshot (quantity 0; zero-value stock entries skipped, FE Q36). */
    static List<ProductionCostAdjustment> adjustments(WorkOrder workOrder, UUID runId, CostSnapshot snapshot, String reason) {
        List<ProductionCostAdjustment> entries = new ArrayList<>();
        int lineNo = 0;
        for (ReceiptAllocation receipt : snapshot.allocations()) {
            for (PartAllocation part : receipt.parts()) {
                for (DeliveryAllocation delivery : part.deliveries()) {
                    entries.add(entry(workOrder, runId, ++lineNo, receipt, part, reason)
                            .deliveryId(delivery.deliveryId())
                            .deliveryLineId(delivery.deliveryLineId())
                            .adjustedMovementId(delivery.movementId())
                            .coveredQuantity(delivery.quantity())
                            .stockValueDelta(BigDecimal.ZERO.setScale(6))
                            .allocatedAmount(delivery.amount())
                            .build());
                }
                if (part.remainingQuantity().signum() != 0 || part.remainingValue().signum() != 0) {
                    entries.add(entry(workOrder, runId, ++lineNo, receipt, part, reason)
                            .coveredQuantity(part.remainingQuantity())
                            .stockValueDelta(part.remainingValue())
                            .allocatedAmount(part.remainingValue())
                            .stockValueAfter(part.remainingValue())
                            .build());
                }
            }
        }
        return entries;
    }

    private static ProductionCostAdjustment.ProductionCostAdjustmentBuilder entry(WorkOrder workOrder, UUID runId, int lineNo,
                                                                                ReceiptAllocation receipt,
                                                                                PartAllocation part, String reason) {
        return ProductionCostAdjustment.builder()
                .runId(runId)
                .workOrderId(workOrder.getWorkOrderId())
                .companyId(workOrder.getCompany().getCompanyId())
                .plantId(workOrder.getPlant().getPlantId())
                .itemId(workOrder.getProductItem().getItemId())
                .warehouseId(receipt.warehouseId())
                .lineNo(lineNo)
                .receiptId(receipt.receiptId())
                .decisionId(part.decisionId())
                .quality(part.quality())
                .lotId(part.lotId())
                .reason(reason);
    }

    // ------------------------------------------------------------------ C12

    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.COST_VIEW_ON_WORK_ORDER)
    public PageResult<Adjustment> adjustments(UUID workOrderId, UUID runId, Pageable pageable) {
        ProductionCostRun run = runRepository.findByRunIdAndWorkOrderId(runId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Cost run", runId));
        Page<ProductionCostAdjustment> page = adjustmentRepository.findByRunIdOrderByLineNo(runId, pageable);
        Map<UUID, String> usernames = costService.usernames(page.getContent().stream().map(ProductionCostAdjustment::getCreatedBy));
        return PageResult.from(page.map(entry -> new Adjustment(entry.getAdjustmentId(), "COST_ADJUSTMENT",
                entry.getCompanyId(), entry.getPlantId(), entry.getWorkOrderId(), run.getRunId(), entry.getItemId(),
                entry.getWarehouseId(), entry.getReceiptId(), entry.getLotId(), entry.getQuality(), entry.getDeliveryId(),
                entry.getDeliveryLineId(), entry.getAdjustedMovementId(), entry.getQuantity(), entry.getStockValueDelta(),
                entry.getAllocatedAmount(), entry.getStockValueBefore(), entry.getStockValueAfter(), entry.getCreatedAt(),
                ProductionCostService.actor(entry.getCreatedBy(), usernames), entry.getReason())));
    }

    // ------------------------------------------------------------------ C11

    /**
     * The production cost of each line of a delivery: lines delivered before the close carry the entry written at close;
     * lines delivered after it take a cumulative share of the value left on hand for their lot (lot-tracked output
     * only). Anything else is unknown — never 0 (FE R06). Needs the delivery read authority and cost view on its plant.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@deliveryPermissionGuard.canReadDelivery(authentication, #deliveryId)")
    public DeliveryCost deliveryCost(UUID deliveryId) {
        Delivery delivery = deliveryLookupService.findDelivery(deliveryId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Delivery", deliveryId));
        if (!costService.hasCostView(delivery.getPlantId())) {
            throw new AccessDeniedException("Access denied");
        }
        List<DeliveryLine> lines = deliveryLookupService.findLinesOfDelivery(deliveryId);
        Map<UUID, List<ProductionCostAdjustment>> byLine = lines.isEmpty() ? Map.of()
                : adjustmentRepository.findByDeliveryLineIdIn(lines.stream().map(DeliveryLine::getDeliveryLineId).toList())
                .stream().collect(Collectors.groupingBy(ProductionCostAdjustment::getDeliveryLineId));
        List<UUID> lotIds = lines.stream().map(DeliveryLine::getLotId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, ProductionCostAdjustment> stockByLot = lotIds.isEmpty() ? Map.of()
                : adjustmentRepository.findByLotIdInAndDeliveryLineIdIsNull(lotIds).stream()
                .collect(Collectors.toMap(ProductionCostAdjustment::getLotId, entry -> entry, (a, b) -> a));
        List<DeliveryCostLine> result = new ArrayList<>();
        for (DeliveryLine line : lines) {
            List<ProductionCostAdjustment> entries = byLine.getOrDefault(line.getDeliveryLineId(), List.of());
            if (!entries.isEmpty()) {
                BigDecimal amount = CostMath.c6(entries.stream().map(ProductionCostAdjustment::getAllocatedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                result.add(new DeliveryCostLine(line.getDeliveryLineId(), line.getLineNo(), line.getItemId(),
                        line.getLotId(), line.getQuantity(), amount, true,
                        entries.stream().map(ProductionCostAdjustment::getAdjustmentId).toList(), null));
                continue;
            }
            ProductionCostAdjustment stock = line.getLotId() == null ? null : stockByLot.get(line.getLotId());
            BigDecimal amount = stock == null ? null : postCloseShare(stock, line, delivery);
            result.add(new DeliveryCostLine(line.getDeliveryLineId(), line.getLineNo(), line.getItemId(), line.getLotId(),
                    line.getQuantity(), amount, amount != null,
                    stock == null ? List.of() : List.of(stock.getAdjustmentId()),
                    amount != null ? null : (stock == null ? "NOT_FINALIZED" : "UNRECONCILED_VALUE")));
        }
        boolean complete = result.stream().allMatch(DeliveryCostLine::known);
        BigDecimal known = CostMath.c6(result.stream().map(DeliveryCostLine::amount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new DeliveryCost(deliveryId, delivery.getCode(), ProductionCostRate.CURRENCY, known, complete, result);
    }

    /**
     * Cumulative share of the value left on hand at close, over the lines of the same lot delivered after the close in
     * posting order: {@code C6(V × cumulative / Q) − C6(V × previous / Q)}. Unknown when more left than was on hand.
     */
    private BigDecimal postCloseShare(ProductionCostAdjustment stock, DeliveryLine target, Delivery targetDelivery) {
        ProductionCostRun run = runRepository.findById(stock.getRunId()).orElse(null);
        if (run == null || run.getFinalizedAt() == null || targetDelivery.getPostedAt() == null
                || targetDelivery.getPostedAt().isBefore(run.getFinalizedAt())) {
            return null;
        }
        BigDecimal previous = BigDecimal.ZERO;
        BigDecimal cumulative = null;
        for (DeliveryLine line : deliveryLookupService.findPostedPoolLines(stock.getItemId(), stock.getWarehouseId())) {
            if (!Objects.equals(line.getLotId(), stock.getLotId())
                    || line.getDelivery().getPostedAt().isBefore(run.getFinalizedAt())) {
                continue;
            }
            if (line.getDeliveryLineId().equals(target.getDeliveryLineId())) {
                cumulative = previous.add(line.getQuantity());
                break;
            }
            previous = previous.add(line.getQuantity());
        }
        BigDecimal covered = stock.getCoveredQuantity();
        if (cumulative == null || covered.signum() == 0 || cumulative.compareTo(covered) > 0) {
            return null;
        }
        BigDecimal value = stock.getStockValueAfter();
        return CostMath.c6(CostMath.share(value, cumulative, covered).subtract(CostMath.share(value, previous, covered)));
    }
}
