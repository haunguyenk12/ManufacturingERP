package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessLineResponse;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gate 1a – a work order may only be released once every component requirement is
 * fully covered by an active material reservation.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderReleaseGate {

    private final WorkOrderRepository workOrderRepository;
    private final MaterialReservationRepository reservationRepository;
    private final WorkOrderBlockRecorder blockRecorder;

    /**
     * Material readiness per component line. One aggregate query for the whole work order.
     */
    public List<WorkOrderMaterialReadinessLineResponse> evaluate(WorkOrder workOrder) {
        Map<UUID, BigDecimal> reservedByLine = reservationRepository
                .sumActiveRemainingByWorkOrderId(workOrder.getWorkOrderId()).stream()
                .collect(Collectors.toMap(
                        ComponentQuantityProjection::getComponentLineId,
                        ComponentQuantityProjection::getQuantity));

        return workOrder.getComponentLines().stream()
                .map(line -> toReadinessLine(line, reservedByLine.getOrDefault(
                        line.getComponentLineId(), BigDecimal.ZERO)))
                .toList();
    }

    /**
     * Refuses release when reservation does not cover the outstanding requirement and
     * records the work order as {@code BLOCKED}.
     * <p>
     * The {@code BLOCKED} write is delegated to {@link WorkOrderBlockRecorder} rather than done here,
     * and the refusal is thrown only afterwards. That split is the whole point: this method used to
     * be {@code REQUIRES_NEW} and throw itself, which marked its own transaction rollback-only and
     * silently discarded the state it had just saved (debt #23, found by {@code ProductionFlowE2EIT}).
     * The recorder returns normally, so its transaction commits and the state survives the caller's
     * rollback — which is what lets planners query which work orders are waiting for material.
     */
    @Transactional(readOnly = true)
    public void ensureMaterialReady(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));

        List<WorkOrderMaterialReadinessLineResponse> lines = evaluate(workOrder);
        long shortLines = lines.stream()
                .filter(line -> line.shortageQuantity().compareTo(BigDecimal.ZERO) > 0)
                .count();
        if (shortLines == 0) {
            return;
        }

        blockRecorder.recordBlocked(workOrderId,
                shortLines + "/" + lines.size() + " components short on reservation");
        throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                "Cannot release: " + shortLines + " component(s) not fully reserved");
    }

    private WorkOrderMaterialReadinessLineResponse toReadinessLine(WorkOrderComponentLine line,
                                                                   BigDecimal reservedQuantity) {
        BigDecimal requiredRemaining = line.remainingQuantity();
        BigDecimal shortage = requiredRemaining.subtract(reservedQuantity).max(BigDecimal.ZERO);
        return new WorkOrderMaterialReadinessLineResponse(
                line.getComponentLineId(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getRequiredQuantity(),
                line.getIssuedQuantity(),
                reservedQuantity,
                shortage);
    }
}
