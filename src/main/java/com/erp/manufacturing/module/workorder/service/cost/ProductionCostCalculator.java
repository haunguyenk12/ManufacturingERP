package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.Baseline;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineMaterial;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineOperation;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.CostTotals;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostCheck;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostSnapshot;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ExecutionTime;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.MaterialLine;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.MaterialReturnValue;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.OperationCost;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.PartAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Quantities;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ReceiptAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.SourceRef;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.VarianceSplit;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G11b: the production cost of a work order as a pure function of its sources (FE §3.5). No repository, no clock —
 * every number here is reproducible from {@link CostInputs}, which is what makes the source version meaningful and the
 * finalized snapshot re-checkable (FE A21).
 *
 * <ul>
 *   <li>Material: each posted issue line is valued at the baseline standard cost of its component (D1);
 *       {@code issued = consumed + returned + unused} in quantity and value; returns take the cumulative share of the
 *       issued value and the last one that empties the line takes the residual (FE §3.5 #4-5, GĐ8 Q37).</li>
 *   <li>Time: the duration of every execution, a retry counted once as its own record (FE Q11); a missing, empty or
 *       overlapping duration inside one operation makes that operation's time unknown, never 0 (D11).</li>
 *   <li>Allocation: from the authoritative total, cumulative over receipts, then QC parts, then deliveries — the last
 *       of each level takes the residual, so the parts always add back to the whole (FE Q19).</li>
 * </ul>
 */
public final class ProductionCostCalculator {

    public static final String MISSING_BASELINE = ProductionCostBaselineService.MISSING_BASELINE;
    public static final String MISSING_PRICE = ProductionCostBaselineService.MISSING_PRICE;
    public static final String MISSING_RATE = ProductionCostBaselineService.MISSING_RATE;
    public static final String MISSING_CONSUMPTION = "MISSING_CONSUMPTION";
    public static final String INVALID_TIME = "INVALID_TIME";
    public static final String LEGACY_SOURCE = "LEGACY_SOURCE";
    public static final String UNRECONCILED_VALUE = "UNRECONCILED_VALUE";

    public static final String CHECK_STATE = "STATE";
    public static final String CHECK_OPERATIONS = "OPERATIONS";
    public static final String CHECK_ISSUES = "ISSUES";
    public static final String CHECK_MATERIAL = "MATERIAL";
    public static final String CHECK_RESERVATIONS = "RESERVATIONS";
    public static final String CHECK_RECEIPTS = "RECEIPTS";
    public static final String CHECK_QC = "QC";
    public static final String CHECK_BASELINE = "BASELINE";
    public static final String CHECK_COST = "COST";
    public static final String CHECK_VALUATION = "VALUATION";
    public static final String CHECK_PREVIEW = "PREVIEW";

    private static final Comparator<CostInputs.Execution> EXECUTION_ORDER = Comparator
            .comparing(CostInputs.Execution::startedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(execution -> execution.executionId().toString());

    private ProductionCostCalculator() {
    }

    public static CostSnapshot calculate(CostInputs in) {
        Set<String> unknown = new LinkedHashSet<>();
        Baseline baseline = in.baseline();
        if (baseline == null) {
            unknown.add(MISSING_BASELINE);
        } else {
            unknown.addAll(ProductionCostBaselineService.unknownReasons(baseline));
        }

        MaterialResult material = materials(in, baseline, unknown);
        TimeResult time = operations(in, baseline, unknown);

        CostTotals planned = baseline == null ? new CostTotals(null, null, null, null)
                : ProductionCostBaselineService.planned(baseline.materials(), baseline.operations());
        CostTotals actual = totals(material.amount(), time.labor(), time.overhead());

        BigDecimal good = in.finalGood();
        BigDecimal received = sum(in.receipts().stream().map(CostInputs.Receipt::quantity).toList());
        BigDecimal accepted = sum(in.receipts().stream().flatMap(r -> r.decisions().stream())
                .map(CostInputs.Decision::accepted).toList());
        BigDecimal rejected = sum(in.receipts().stream().flatMap(r -> r.decisions().stream())
                .map(CostInputs.Decision::rejected).toList());
        BigDecimal pending = received.subtract(accepted).subtract(rejected);
        BigDecimal shortfall = in.plannedQuantity().subtract(good).max(BigDecimal.ZERO);
        Quantities quantities = new Quantities(in.plannedQuantity(), good, in.scrap(), in.rework(), shortfall, received,
                accepted, rejected, pending);

        AllocationResult allocation = allocate(in, actual.total());
        BigDecimal unitCost = good.signum() > 0 && actual.total() != null
                ? CostMath.c6(actual.total().divide(good, CostMath.CONTEXT)) : null;
        BigDecimal loss = good.signum() == 0 && actual.total() != null ? actual.total() : null;

        VarianceSplit variance = new VarianceSplit(difference(actual.material(), planned.material()),
                difference(actual.labor(), planned.labor()), difference(actual.overhead(), planned.overhead()),
                difference(actual.total(), planned.total()));
        BigDecimal variancePercent = variance.total() != null && planned.total().signum() > 0
                ? CostMath.c6(variance.total().multiply(CostMath.HUNDRED, CostMath.CONTEXT)
                .divide(planned.total(), CostMath.CONTEXT)) : null;
        if (allocation.unreconciled() || material.valueUnreconciled()) {
            unknown.add(UNRECONCILED_VALUE);
        }

        List<CostCheck> checks = checks(in, baseline, material, time, quantities, planned, actual, allocation);
        return new CostSnapshot(quantities, ProductionCostRate.CURRENCY, planned, actual, variance, variancePercent,
                time.plannedMinutes(), time.actualMinutes(), unitCost, loss,
                allocation.allocations() == null ? null : allocation.remainingValue(),
                allocation.allocations() == null ? null : allocation.issuedValue(),
                List.copyOf(unknown), material.lines(), time.operations(),
                allocation.allocations() == null ? List.of() : allocation.allocations(), checks);
    }

    // ---------------------------------------------------------------- material

    private record MaterialResult(List<MaterialLine> lines, BigDecimal amount, boolean consumptionKnown,
                                  boolean missingEvidence, boolean valueUnreconciled, MaterialLine firstUnused) {
    }

    private static MaterialResult materials(CostInputs in, Baseline baseline, Set<String> unknown) {
        Map<UUID, BigDecimal> unitCosts = new LinkedHashMap<>();
        if (baseline != null) {
            for (BaselineMaterial m : baseline.materials()) {
                unitCosts.put(m.componentLineId(), m.unitCost());
            }
        }
        boolean consumptionKnown = in.operationTracking();
        boolean valueUnreconciled = false;
        boolean anyUnknownValue = false;
        MaterialLine firstUnused = null;
        List<MaterialLine> lines = new ArrayList<>();
        for (CostInputs.IssueLine line : in.issueLines()) {
            BigDecimal qi = line.quantity();
            BigDecimal unit = unitCosts.get(line.componentLineId());
            BigDecimal vi = unit == null ? null : CostMath.c6(qi.multiply(unit, CostMath.CONTEXT));
            BigDecimal qc = consumptionKnown ? line.consumed() : null;
            BigDecimal qr = sum(line.returns().stream().map(CostInputs.Return::quantity).toList());
            BigDecimal qu = qc == null ? null : qi.subtract(qc).subtract(qr);
            boolean emptied = qu != null && qu.signum() == 0;

            List<MaterialReturnValue> returns = new ArrayList<>();
            BigDecimal cumulative = BigDecimal.ZERO;
            BigDecimal valued = BigDecimal.ZERO;
            for (int i = 0; i < line.returns().size(); i++) {
                CostInputs.Return ret = line.returns().get(i);
                cumulative = cumulative.add(ret.quantity());
                BigDecimal value = null;
                if (vi != null) {
                    boolean last = i == line.returns().size() - 1;
                    BigDecimal target = last && emptied
                            ? vi.subtract(CostMath.share(vi, qc, qi))
                            : CostMath.share(vi, cumulative, qi);
                    value = CostMath.c6(target.subtract(valued));
                    if (value.signum() < 0) {
                        valueUnreconciled = true;
                    }
                    valued = valued.add(value);
                }
                returns.add(new MaterialReturnValue(ret.returnId(), ret.code(), ret.returnLineId(), ret.movementId(),
                        ret.quantity(), value, ret.postedAt()));
            }
            BigDecimal vr = vi == null ? null : CostMath.c6(valued);
            BigDecimal vc = null;
            BigDecimal vu = null;
            if (vi != null && qc != null) {
                vc = emptied ? CostMath.c6(vi.subtract(vr)) : CostMath.share(vi, qc, qi);
                vu = CostMath.c6(vi.subtract(vc).subtract(vr));
                if (vc.signum() < 0 || vu.signum() < 0) {
                    valueUnreconciled = true;
                    vc = null;
                    vu = null;
                }
            }
            if (vc == null) {
                anyUnknownValue = true;
            }
            MaterialLine row = new MaterialLine(line.issueId(), line.issueCode(), line.issueLineId(), line.movementId(),
                    line.componentLineId(), line.itemId(), line.itemCode(), line.warehouseId(), line.lotId(),
                    line.uom(), qi, qc, qr, qu, unit, vi, vc, vr, vu, returns);
            if (firstUnused == null && qu != null && qu.signum() != 0) {
                firstUnused = row;
            }
            lines.add(row);
        }
        Set<UUID> evidenced = in.issueLines().stream().map(CostInputs.IssueLine::componentLineId)
                .collect(Collectors.toSet());
        boolean missingEvidence = in.requirements().stream()
                .anyMatch(r -> r.requiredQuantity().signum() > 0 && !evidenced.contains(r.componentLineId()));
        if (!consumptionKnown) {
            unknown.add(LEGACY_SOURCE);
        }
        if (missingEvidence) {
            unknown.add(MISSING_CONSUMPTION);
        }
        BigDecimal amount = anyUnknownValue || missingEvidence || !consumptionKnown ? null
                : CostMath.sumKnown(lines.stream().map(MaterialLine::consumedValue).toList());
        return new MaterialResult(lines, amount, consumptionKnown, missingEvidence, valueUnreconciled, firstUnused);
    }

    // ---------------------------------------------------------------- time

    private record TimeResult(List<OperationCost> operations, BigDecimal labor, BigDecimal overhead,
                              BigDecimal plannedMinutes, BigDecimal actualMinutes, boolean invalidTime,
                              boolean untracked, boolean missingRate) {
    }

    private static TimeResult operations(CostInputs in, Baseline baseline, Set<String> unknown) {
        Map<UUID, BaselineOperation> planned = baseline == null ? Map.of()
                : baseline.operations().stream().collect(Collectors.toMap(BaselineOperation::operationId,
                Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, List<CostInputs.Execution>> byOperation = new LinkedHashMap<>();
        boolean untracked = false;
        for (CostInputs.Execution execution : in.executions()) {
            if (execution.operationId() == null) {
                untracked = true;
            } else {
                byOperation.computeIfAbsent(execution.operationId(), id -> new ArrayList<>()).add(execution);
            }
        }
        boolean invalidTime = false;
        boolean missingRate = false;
        List<OperationCost> rows = new ArrayList<>();
        List<BigDecimal> labor = new ArrayList<>();
        List<BigDecimal> overhead = new ArrayList<>();
        List<BigDecimal> plannedMinutes = new ArrayList<>();
        List<BigDecimal> actualMinutes = new ArrayList<>();
        for (CostInputs.Operation operation : in.operations()) {
            BaselineOperation base = planned.get(operation.operationId());
            List<CostInputs.Execution> executions = new ArrayList<>(byOperation.getOrDefault(operation.operationId(), List.of()));
            executions.sort(EXECUTION_ORDER);
            List<ExecutionTime> times = new ArrayList<>();
            BigDecimal minutes = BigDecimal.ZERO;
            boolean valid = true;
            java.time.Instant latestEnd = null;
            for (CostInputs.Execution execution : executions) {
                boolean ok = execution.startedAt() != null && execution.endedAt() != null
                        && execution.endedAt().isAfter(execution.startedAt())
                        && (latestEnd == null || !execution.startedAt().isBefore(latestEnd));
                BigDecimal duration = null;
                if (execution.startedAt() != null && execution.endedAt() != null) {
                    duration = CostMath.c12(BigDecimal.valueOf(Duration.between(execution.startedAt(), execution.endedAt())
                            .toNanos()).divide(BigDecimal.valueOf(60_000_000_000L), CostMath.CONTEXT));
                    if (latestEnd == null || execution.endedAt().isAfter(latestEnd)) {
                        latestEnd = execution.endedAt();
                    }
                }
                if (ok) {
                    minutes = minutes.add(duration);
                } else {
                    valid = false;
                }
                times.add(new ExecutionTime(execution.executionId(), execution.code(), execution.sourceExecutionId(),
                        execution.startedAt(), execution.endedAt(), CostMath.c6(duration), ok));
            }
            invalidTime |= !valid;
            BigDecimal actual = valid ? CostMath.c12(minutes) : null;
            BigDecimal laborRate = base == null ? null : base.laborRate();
            BigDecimal overheadRate = base == null ? null : base.overheadRate();
            missingRate |= base != null && laborRate == null;
            BigDecimal actualLabor = CostMath.minutesCost(actual, laborRate);
            BigDecimal actualOverhead = CostMath.minutesCost(actual, overheadRate);
            labor.add(actualLabor);
            overhead.add(actualOverhead);
            plannedMinutes.add(base == null ? null : base.plannedMinutes());
            actualMinutes.add(actual);
            rows.add(new OperationCost(operation.operationId(), operation.sequence(), operation.name(),
                    base == null ? null : base.workCenterId(), base == null ? null : base.capacityUnits(),
                    base == null ? null : base.rateVersion(), laborRate, overheadRate,
                    base == null ? null : base.plannedMinutes(), actual,
                    base == null ? null : base.plannedLabor(), base == null ? null : base.plannedOverhead(),
                    actualLabor, actualOverhead, times));
        }
        if (invalidTime) {
            unknown.add(INVALID_TIME);
        }
        if (untracked) {
            unknown.add(LEGACY_SOURCE);
        }
        BigDecimal laborTotal = untracked ? null : CostMath.sumKnown(labor);
        BigDecimal overheadTotal = untracked ? null : CostMath.sumKnown(overhead);
        return new TimeResult(rows, laborTotal, overheadTotal, CostMath.c12(sumOrNull(plannedMinutes)),
                CostMath.c12(sumOrNull(actualMinutes)), invalidTime, untracked, missingRate);
    }

    // ---------------------------------------------------------------- allocation

    private record AllocationResult(List<ReceiptAllocation> allocations, BigDecimal remainingValue,
                                    BigDecimal issuedValue, boolean unreconciled, boolean unmatchedDelivery) {
    }

    /** A mutable QC part while deliveries are assigned to it. */
    private static final class Part {
        final String quality;
        final UUID decisionId;
        final UUID lotId;
        final BigDecimal quantity;
        BigDecimal assigned = BigDecimal.ZERO;
        final List<CostInputs.DeliveryLine> lines = new ArrayList<>();
        final List<BigDecimal> lineQuantities = new ArrayList<>();

        Part(String quality, UUID decisionId, UUID lotId, BigDecimal quantity) {
            this.quality = quality;
            this.decisionId = decisionId;
            this.lotId = lotId;
            this.quantity = quantity;
        }
    }

    private static AllocationResult allocate(CostInputs in, BigDecimal total) {
        BigDecimal good = in.finalGood();
        Map<CostInputs.Receipt, List<Part>> partsByReceipt = new LinkedHashMap<>();
        List<Part> allParts = new ArrayList<>();
        for (CostInputs.Receipt receipt : in.receipts()) {
            List<Part> parts = new ArrayList<>();
            BigDecimal decided = BigDecimal.ZERO;
            for (CostInputs.Decision decision : receipt.decisions()) {
                if (decision.accepted().signum() > 0) {
                    parts.add(new Part("ACCEPTED", decision.decisionId(),
                            decision.acceptedLotId() != null ? decision.acceptedLotId() : receipt.lotId(),
                            decision.accepted()));
                }
                if (decision.rejected().signum() > 0) {
                    parts.add(new Part("REJECTED", decision.decisionId(),
                            decision.rejectedLotId() != null ? decision.rejectedLotId() : receipt.lotId(),
                            decision.rejected()));
                }
                decided = decided.add(decision.accepted()).add(decision.rejected());
            }
            BigDecimal pending = receipt.quantity().subtract(decided);
            if (pending.signum() > 0) {
                parts.add(new Part("PENDING", null, receipt.lotId(), pending));
            }
            partsByReceipt.put(receipt, parts);
            allParts.addAll(parts);
        }
        boolean unmatched = false;
        for (CostInputs.DeliveryLine line : in.deliveries()) {
            BigDecimal left = line.quantity();
            for (Part part : allParts) {
                if (left.signum() == 0) {
                    break;
                }
                if (!"ACCEPTED".equals(part.quality) || !Objects.equals(part.lotId, line.lotId())) {
                    continue;
                }
                BigDecimal room = part.quantity.subtract(part.assigned);
                if (room.signum() <= 0) {
                    continue;
                }
                BigDecimal take = room.min(left);
                part.assigned = part.assigned.add(take);
                part.lines.add(line);
                part.lineQuantities.add(take);
                left = left.subtract(take);
            }
            if (left.signum() > 0) {
                unmatched = true;
            }
        }
        boolean amounts = total != null && good.signum() > 0;
        List<ReceiptAllocation> allocations = new ArrayList<>();
        BigDecimal remainingValue = BigDecimal.ZERO;
        BigDecimal issuedValue = BigDecimal.ZERO;
        BigDecimal cumulativeReceived = BigDecimal.ZERO;
        BigDecimal allocatedReceipts = BigDecimal.ZERO;
        for (Map.Entry<CostInputs.Receipt, List<Part>> entry : partsByReceipt.entrySet()) {
            CostInputs.Receipt receipt = entry.getKey();
            cumulativeReceived = cumulativeReceived.add(receipt.quantity());
            BigDecimal receiptAmount = null;
            if (amounts) {
                receiptAmount = CostMath.c6(CostMath.share(total, cumulativeReceived, good).subtract(allocatedReceipts));
                allocatedReceipts = allocatedReceipts.add(receiptAmount);
            }
            List<PartAllocation> parts = new ArrayList<>();
            BigDecimal cumulativePart = BigDecimal.ZERO;
            BigDecimal allocatedParts = BigDecimal.ZERO;
            for (Part part : entry.getValue()) {
                cumulativePart = cumulativePart.add(part.quantity);
                BigDecimal partAmount = null;
                if (receiptAmount != null) {
                    partAmount = CostMath.c6(CostMath.share(receiptAmount, cumulativePart, receipt.quantity())
                            .subtract(allocatedParts));
                    allocatedParts = allocatedParts.add(partAmount);
                }
                List<DeliveryAllocation> deliveries = new ArrayList<>();
                BigDecimal cumulativeDelivered = BigDecimal.ZERO;
                BigDecimal allocatedDeliveries = BigDecimal.ZERO;
                for (int i = 0; i < part.lines.size(); i++) {
                    CostInputs.DeliveryLine line = part.lines.get(i);
                    BigDecimal quantity = part.lineQuantities.get(i);
                    cumulativeDelivered = cumulativeDelivered.add(quantity);
                    BigDecimal amount = null;
                    if (partAmount != null) {
                        amount = CostMath.c6(CostMath.share(partAmount, cumulativeDelivered, part.quantity)
                                .subtract(allocatedDeliveries));
                        allocatedDeliveries = allocatedDeliveries.add(amount);
                    }
                    deliveries.add(new DeliveryAllocation(line.deliveryId(), line.code(), line.lineId(),
                            line.movementId(), quantity, amount));
                }
                BigDecimal remainingQuantity = part.quantity.subtract(part.assigned);
                BigDecimal partRemaining = partAmount == null ? null : CostMath.c6(partAmount.subtract(allocatedDeliveries));
                if (partAmount != null) {
                    remainingValue = remainingValue.add(partRemaining);
                    issuedValue = issuedValue.add(allocatedDeliveries);
                }
                parts.add(new PartAllocation(part.quality, part.decisionId, part.lotId, part.quantity, partAmount,
                        remainingQuantity, partRemaining, deliveries));
            }
            allocations.add(new ReceiptAllocation(receipt.receiptId(), receipt.code(), receipt.warehouseId(),
                    receipt.approvedAt(), receipt.quantity(), receiptAmount, parts));
        }
        boolean poolMixed = in.baseline() == null || in.baseline().outputOpeningQuantity().signum() != 0
                || in.foreignPoolMovements() > 0;
        return new AllocationResult(allocations, amounts ? CostMath.c6(remainingValue) : null,
                amounts ? CostMath.c6(issuedValue) : null, poolMixed || unmatched, unmatched);
    }

    // ---------------------------------------------------------------- checks

    private static List<CostCheck> checks(CostInputs in, Baseline baseline, MaterialResult material, TimeResult time,
                                          Quantities q, CostTotals planned, CostTotals actual,
                                          AllocationResult allocation) {
        SourceRef workOrder = new SourceRef("WORK_ORDER", in.workOrderId(), null);
        List<CostCheck> checks = new ArrayList<>();

        checks.add(in.status() == WorkOrderStatus.COMPLETED ? ok(CHECK_STATE)
                : fail(CHECK_STATE, List.of("STATUS_" + in.status()), workOrder));

        List<String> operations = new ArrayList<>();
        if (!in.operationTracking()) {
            operations.add("LEGACY_TRACKING");
        }
        if (in.operations().isEmpty()) {
            operations.add("NO_OPERATIONS");
        }
        for (CostInputs.Operation operation : in.operations()) {
            addOnce(operations, !operation.completed(), "OPERATION_OPEN");
            addOnce(operations, operation.input().compareTo(operation.freshProcessed()) != 0, "INPUT_REMAINING");
            addOnce(operations, operation.pendingRework().signum() > 0, "REWORK_PENDING");
            addOnce(operations, !operation.last() && operation.transferable().signum() > 0, "TRANSFER_REMAINING");
            addOnce(operations, operation.last() && operation.good().compareTo(in.finalGood()) != 0, "FINAL_GOOD_MISMATCH");
        }
        checks.add(operations.isEmpty() ? ok(CHECK_OPERATIONS) : fail(CHECK_OPERATIONS, operations, workOrder));

        checks.add(in.pendingIssues() ? fail(CHECK_ISSUES, List.of("ISSUE_PENDING_APPROVAL"), workOrder) : ok(CHECK_ISSUES));

        List<String> materialReasons = new ArrayList<>();
        addOnce(materialReasons, !material.consumptionKnown(), "CONSUMPTION_UNKNOWN");
        addOnce(materialReasons, material.missingEvidence(), MISSING_CONSUMPTION);
        addOnce(materialReasons, material.firstUnused() != null, "UNUSED_MATERIAL");
        addOnce(materialReasons, material.valueUnreconciled(), "MATERIAL_VALUE_UNRECONCILED");
        SourceRef unusedSource = material.firstUnused() == null ? null
                : new SourceRef("MATERIAL_ISSUE", material.firstUnused().issueId(), material.firstUnused().issueCode());
        checks.add(materialReasons.isEmpty() ? ok(CHECK_MATERIAL) : fail(CHECK_MATERIAL, materialReasons, unusedSource));

        checks.add(in.activeReservedQuantity().signum() > 0
                ? fail(CHECK_RESERVATIONS, List.of("ACTIVE_RESERVATION"), workOrder) : ok(CHECK_RESERVATIONS));

        List<String> receipts = new ArrayList<>();
        addOnce(receipts, in.openReceipts(), "RECEIPT_OPEN");
        addOnce(receipts, q.approvedReceiptQuantity().compareTo(q.finalGoodQuantity()) != 0, "RECEIPT_QUANTITY_MISMATCH");
        checks.add(receipts.isEmpty() ? ok(CHECK_RECEIPTS) : fail(CHECK_RECEIPTS, receipts, workOrder));

        CostInputs.Receipt pendingReceipt = in.receipts().stream()
                .filter(r -> r.quantity().subtract(sum(r.decisions().stream().map(CostInputs.Decision::accepted).toList()))
                        .subtract(sum(r.decisions().stream().map(CostInputs.Decision::rejected).toList())).signum() != 0)
                .findFirst().orElse(null);
        checks.add(q.qcPendingQuantity().signum() == 0 ? ok(CHECK_QC) : fail(CHECK_QC, List.of("QC_PENDING"),
                pendingReceipt == null ? null : new SourceRef("PRODUCTION_RECEIPT", pendingReceipt.receiptId(), pendingReceipt.code())));

        List<String> baselineReasons = new ArrayList<>();
        if (baseline == null) {
            baselineReasons.add(MISSING_BASELINE);
        } else {
            baselineReasons.addAll(ProductionCostBaselineService.unknownReasons(baseline));
            Set<UUID> captured = baseline.operations().stream().map(BaselineOperation::operationId).collect(Collectors.toSet());
            addOnce(baselineReasons, in.operations().stream().anyMatch(o -> !captured.contains(o.operationId())),
                    "OPERATION_NOT_IN_BASELINE");
        }
        checks.add(baselineReasons.isEmpty() ? ok(CHECK_BASELINE) : fail(CHECK_BASELINE, baselineReasons, workOrder));

        List<String> costReasons = new ArrayList<>();
        addOnce(costReasons, planned.total() == null, "PLANNED_UNKNOWN");
        addOnce(costReasons, actual.material() == null, "MATERIAL_UNKNOWN");
        addOnce(costReasons, time.invalidTime(), INVALID_TIME);
        addOnce(costReasons, time.untracked(), LEGACY_SOURCE);
        addOnce(costReasons, actual.labor() == null || actual.overhead() == null, "TIME_COST_UNKNOWN");
        addOnce(costReasons, actual.total() != null && actual.total().compareTo(CostMath.MAX_AMOUNT) > 0, "COST_VALUE_OVERFLOW");
        checks.add(costReasons.isEmpty() ? ok(CHECK_COST) : fail(CHECK_COST, costReasons, workOrder));

        List<String> valuation = new ArrayList<>();
        if (baseline == null) {
            valuation.add(MISSING_BASELINE);
        } else {
            addOnce(valuation, baseline.outputOpeningQuantity().signum() != 0, "POOL_OPENING_STOCK");
        }
        addOnce(valuation, in.foreignPoolMovements() > 0, "POOL_MIXED");
        addOnce(valuation, allocation.unmatchedDelivery(), "DELIVERY_UNMATCHED");
        addOnce(valuation, actual.total() == null, "ACTUAL_UNKNOWN");
        addOnce(valuation, q.finalGoodQuantity().signum() == 0 && q.approvedReceiptQuantity().signum() != 0,
                "RECEIPT_WITHOUT_OUTPUT");
        checks.add(valuation.isEmpty() ? ok(CHECK_VALUATION) : fail(CHECK_VALUATION, valuation, workOrder));
        return checks;
    }

    // ---------------------------------------------------------------- helpers

    private static CostCheck ok(String code) {
        return new CostCheck(code, true, List.of(), null);
    }

    private static CostCheck fail(String code, List<String> reasons, SourceRef source) {
        return new CostCheck(code, false, List.copyOf(reasons), source);
    }

    private static void addOnce(List<String> reasons, boolean condition, String reason) {
        if (condition && !reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    private static CostTotals totals(BigDecimal material, BigDecimal labor, BigDecimal overhead) {
        BigDecimal total = material == null || labor == null || overhead == null ? null
                : CostMath.c6(material.add(labor).add(overhead));
        return new CostTotals(material, labor, overhead, total);
    }

    private static BigDecimal difference(BigDecimal actual, BigDecimal planned) {
        return actual == null || planned == null ? null : CostMath.c6(actual.subtract(planned));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOrNull(List<BigDecimal> values) {
        return values.stream().anyMatch(Objects::isNull) ? null : sum(values);
    }
}
