package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessLineResponse;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderMaterialReadinessResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderReleaseGate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view of the release gate: shows which components are short on reservation
 * before the caller attempts {@code POST /work-orders/{id}/release}.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderReadinessService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderReleaseGate releaseGate;

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_WORK_ORDER_READ', #workOrderId)")
    public WorkOrderMaterialReadinessResponse getMaterialReadiness(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));

        List<WorkOrderMaterialReadinessLineResponse> lines = releaseGate.evaluate(workOrder);
        int shortageLineCount = (int) lines.stream()
                .filter(line -> line.shortageQuantity().compareTo(BigDecimal.ZERO) > 0)
                .count();
        boolean ready = shortageLineCount == 0;

        return new WorkOrderMaterialReadinessResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                ready,
                ready && workOrder.canRelease() && !lines.isEmpty(),
                reservedPercent(lines),
                shortageLineCount,
                lines);
    }

    /**
     * Coverage of the outstanding requirement, aggregated over all component lines rather than
     * averaged per line: one big uncovered component matters more than one small one.
     * A work order whose requirement is already fully issued has nothing left to reserve — that is
     * 100% ready, not a division by zero.
     */
    private BigDecimal reservedPercent(List<WorkOrderMaterialReadinessLineResponse> lines) {
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;
        for (WorkOrderMaterialReadinessLineResponse line : lines) {
            BigDecimal remaining = line.requiredQuantity().subtract(line.issuedQuantity()).max(BigDecimal.ZERO);
            outstanding = outstanding.add(remaining);
            covered = covered.add(line.reservedQuantity().min(remaining));
        }
        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED;
        }
        return covered.multiply(HUNDRED).divide(outstanding, 2, RoundingMode.HALF_UP);
    }
}
