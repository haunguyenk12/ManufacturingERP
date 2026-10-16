package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Entry point for {@code workcenter} (rule C7) to ask whether a work center still carries open load
 * (G5, FE §3.5 "trung tâm có tải mở bị chặn"). Reverse of the usual direction, same shape as
 * {@code LotQcOriginLookupService}. No {@code @PreAuthorize}: the caller is already authorised on the
 * work center.
 */
@Service
@RequiredArgsConstructor
public class WorkCenterLoadLookupService {

    /** Every status that may still run an operation: neither finished nor cancelled. */
    static final List<WorkOrderStatus> OPEN_STATUSES = List.of(
            WorkOrderStatus.DRAFT, WorkOrderStatus.PLANNED, WorkOrderStatus.BLOCKED,
            WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS);

    private final WorkOrderOperationRepository workOrderOperationRepository;

    @Transactional(readOnly = true)
    public boolean hasOpenWorkOrderLoad(UUID workCenterId) {
        return workOrderOperationRepository.existsByWorkCenterWorkCenterIdAndWorkOrderStatusIn(
                workCenterId, OPEN_STATUSES);
    }
}
