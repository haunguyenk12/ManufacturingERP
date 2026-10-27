package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.module.costing.service.ItemStandardCostLookupService;
import com.erp.manufacturing.module.inventory.service.StockPoolLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.ProductionCostBaseline;
import com.erp.manufacturing.module.workorder.domain.ProductionCostBaselineMaterial;
import com.erp.manufacturing.module.workorder.domain.ProductionCostBaselineOperation;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.Baseline;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineMaterial;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineOperation;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineView;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.CostTotals;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.ActorRef;
import com.erp.manufacturing.module.workorder.repository.ProductionCostBaselineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * G11a: captures the cost baseline of a work order inside its release transaction (FE R05) and reads it back (C5).
 *
 * <p>Material price = the component's standard cost at that second (D1 — the warehouse knows no average price);
 * operation minutes = {@code (setup + run × planned quantity) / capacity units} of the work center (FE Q9); rates = the
 * work center's current version. Anything missing stays {@code null}: release is never blocked for money (D4), and a
 * price or rate saved later never changes a baseline already captured (B256).
 */
@Service
@RequiredArgsConstructor
public class ProductionCostBaselineService {

    public static final String MISSING_BASELINE = "MISSING_BASELINE";
    public static final String MISSING_PRICE = "MISSING_PRICE";
    public static final String MISSING_RATE = "MISSING_RATE";

    private final ProductionCostBaselineRepository baselineRepository;
    private final ProductionCostRateService rateService;
    private final ItemStandardCostLookupService standardCostLookupService;
    private final StockPoolLookupService stockPoolLookupService;
    private final SecurityAuditorAware auditorAware;
    private final UserLookupService userLookupService;

    /** Called by {@code WorkOrderService.release} in its own transaction; a second call is a no-op. */
    @Transactional
    public void capture(WorkOrder workOrder, Instant now) {
        if (baselineRepository.existsByWorkOrderId(workOrder.getWorkOrderId())) {
            return;
        }
        UUID outputItemId = workOrder.getProductItem().getItemId();
        UUID outputWarehouseId = workOrder.getOutputWarehouse().getWarehouseId();
        ProductionCostBaseline baseline = ProductionCostBaseline.builder()
                .workOrderId(workOrder.getWorkOrderId())
                .plantId(workOrder.getPlant().getPlantId())
                .outputItemId(outputItemId)
                .outputWarehouseId(outputWarehouseId)
                .outputOpeningQuantity(stockPoolLookupService.onHand(outputItemId, outputWarehouseId))
                .plannedQuantity(workOrder.getPlannedQuantity())
                .capturedAt(now)
                .capturedBy(auditorAware.getCurrentAuditor().orElse(null))
                .build();
        UUID companyId = workOrder.getCompany().getCompanyId();
        for (WorkOrderComponentLine line : workOrder.getComponentLines()) {
            BigDecimal unitCost = standardCostLookupService
                    .findKnownStandardUnitCost(companyId, line.getComponentItem().getItemId())
                    .map(CostMath::c12).orElse(null);
            baseline.getMaterials().add(ProductionCostBaselineMaterial.builder()
                    .baseline(baseline)
                    .lineNo(line.getLineNo())
                    .componentLineId(line.getComponentLineId())
                    .itemId(line.getComponentItem().getItemId())
                    .warehouseId(line.getWarehouse() == null ? null : line.getWarehouse().getWarehouseId())
                    .uom(line.getComponentItem().getUnit())
                    .plannedQuantity(line.getRequiredQuantity())
                    .unitCost(unitCost)
                    .plannedAmount(unitCost == null ? null
                            : CostMath.c6(line.getRequiredQuantity().multiply(unitCost, CostMath.CONTEXT)))
                    .build());
        }
        List<UUID> centerIds = workOrder.getOperations().stream()
                .map(WorkOrderOperation::getWorkCenter).filter(Objects::nonNull)
                .map(WorkCenter::getWorkCenterId).distinct().toList();
        Map<UUID, ProductionCostRate> rates = rateService.currentRates(centerIds);
        for (WorkOrderOperation operation : workOrder.getOperations()) {
            WorkCenter center = operation.getWorkCenter();
            BigDecimal minutes = center == null ? null : plannedMinutes(operation, workOrder.getPlannedQuantity(),
                    center.getCapacityUnits());
            ProductionCostRate rate = center == null ? null : rates.get(center.getWorkCenterId());
            baseline.getOperations().add(ProductionCostBaselineOperation.builder()
                    .baseline(baseline)
                    .workOrderOperationId(operation.getWorkOrderOperationId())
                    .sequence(operation.getSequence())
                    .name(operation.getName())
                    .workCenterId(center == null ? null : center.getWorkCenterId())
                    .capacityUnits(center == null ? null : center.getCapacityUnits())
                    .plannedMinutes(minutes)
                    .rateId(rate == null ? null : rate.getRateId())
                    .rateVersion(rate == null ? null : rate.getRateVersion())
                    .laborRate(rate == null ? null : rate.getLaborRate())
                    .overheadRate(rate == null ? null : rate.getOverheadRate())
                    .plannedLabor(rate == null ? null : CostMath.minutesCost(minutes, rate.getLaborRate()))
                    .plannedOverhead(rate == null ? null : CostMath.minutesCost(minutes, rate.getOverheadRate()))
                    .build());
        }
        baselineRepository.save(baseline);
    }

    /** Whether the work order was released under G11 and so must close through finalize-and-close (D5). */
    @Transactional(readOnly = true)
    public boolean hasBaseline(UUID workOrderId) {
        return baselineRepository.existsByWorkOrderId(workOrderId);
    }

    /** {@code (setup + run × quantity) / capacity units} at 12 decimals — active time, never the calendar span. */
    static BigDecimal plannedMinutes(WorkOrderOperation operation, BigDecimal quantity, Integer capacityUnits) {
        if (capacityUnits == null || capacityUnits <= 0) {
            return null;
        }
        BigDecimal active = operation.getSetupMinutes().add(operation.getRunMinutesPerUnit().multiply(quantity));
        return CostMath.c12(active.divide(BigDecimal.valueOf(capacityUnits), CostMath.CONTEXT));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.COST_VIEW_ON_WORK_ORDER)
    public BaselineView get(UUID workOrderId) {
        Optional<Baseline> baseline = load(workOrderId);
        return new BaselineView(workOrderId, baseline.orElse(null),
                baseline.map(ProductionCostBaselineService::unknownReasons).orElse(List.of(MISSING_BASELINE)));
    }

    /** The baseline of a work order with its lines, or empty. No authorisation: the caller is (costing, close). */
    @Transactional(readOnly = true)
    public Optional<Baseline> load(UUID workOrderId) {
        return baselineRepository.findByWorkOrderId(workOrderId).map(this::toBaseline);
    }

    private Baseline toBaseline(ProductionCostBaseline baseline) {
        List<BaselineMaterial> materials = baselineRepository.findMaterials(baseline.getBaselineId()).stream()
                .map(m -> new BaselineMaterial(m.getComponentLineId(), m.getLineNo(), m.getItemId(), m.getWarehouseId(),
                        m.getUom(), m.getPlannedQuantity(), m.getUnitCost(), m.getPlannedAmount()))
                .toList();
        List<BaselineOperation> operations = baselineRepository.findOperations(baseline.getBaselineId()).stream()
                .map(o -> new BaselineOperation(o.getWorkOrderOperationId(), o.getSequence(), o.getName(),
                        o.getWorkCenterId(), o.getCapacityUnits(), o.getPlannedMinutes(), o.getRateVersion(),
                        o.getLaborRate(), o.getOverheadRate(), o.getPlannedLabor(), o.getPlannedOverhead()))
                .toList();
        UUID actor = baseline.getCapturedBy();
        ActorRef capturedBy = actor == null ? null
                : new ActorRef(actor, userLookupService.findUsernames(List.of(actor)).get(actor));
        return new Baseline(baseline.getBaselineId(), baseline.getCapturedAt(), capturedBy,
                baseline.getOutputWarehouseId(), baseline.getOutputOpeningQuantity(), baseline.getPlannedQuantity(),
                ProductionCostRate.CURRENCY, planned(materials, operations), materials, operations);
    }

    /** Planned totals of a baseline: material = Σ rounded lines; labor/overhead = Σ rounded operations (FE §3.5 #1-2). */
    static CostTotals planned(List<BaselineMaterial> materials, List<BaselineOperation> operations) {
        BigDecimal material = CostMath.sumKnown(materials.stream().map(BaselineMaterial::plannedAmount).toList());
        BigDecimal labor = CostMath.sumKnown(operations.stream().map(BaselineOperation::plannedLabor).toList());
        BigDecimal overhead = CostMath.sumKnown(operations.stream().map(BaselineOperation::plannedOverhead).toList());
        BigDecimal total = material == null || labor == null || overhead == null ? null
                : CostMath.c6(material.add(labor).add(overhead));
        return new CostTotals(material, labor, overhead, total);
    }

    static List<String> unknownReasons(Baseline baseline) {
        List<String> reasons = new ArrayList<>();
        if (baseline.materials().stream().anyMatch(m -> m.unitCost() == null)) {
            reasons.add(MISSING_PRICE);
        }
        if (baseline.operations().stream().anyMatch(o -> o.plannedMinutes() == null || o.laborRate() == null)) {
            reasons.add(MISSING_RATE);
        }
        return reasons;
    }
}
