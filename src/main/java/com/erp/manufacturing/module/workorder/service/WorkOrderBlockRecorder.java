package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists the {@code BLOCKED} state of gate 1a in a transaction of its own (debt #23, fixed in D9).
 *
 * <p>This exists as a separate bean for one reason: it must <b>return normally</b>. The refusal
 * previously lived inside {@code WorkOrderReleaseGate.ensureMaterialReady}, which was annotated
 * {@code REQUIRES_NEW} and then threw — and an exception leaving a transactional method marks that
 * very transaction rollback-only, so the {@code BLOCKED} it had just saved was discarded with it.
 * The work order stayed {@code DRAFT} and B14's promise that planners can query which work orders are
 * waiting for material never held. Writing here and throwing in the caller is what makes the state
 * survive the caller's rollback.
 *
 * <p>Found by {@code ProductionFlowE2EIT}; no unit test could have caught it, because a mocked
 * repository reports a {@code save()} that a rollback later throws away.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderBlockRecorder {

    private final WorkOrderRepository workOrderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(UUID workOrderId, String reason) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
        workOrder.block(Instant.now(), reason);
        workOrderRepository.save(workOrder);
    }
}
