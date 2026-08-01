package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what a unit test <em>can</em> cover here: the state written and the row saved. That the row
 * survives the caller's rollback is a transaction property no mock can express — it is asserted in
 * {@code ProductionFlowE2EIT} against a real database (debt #23).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderBlockRecorder tests")
class WorkOrderBlockRecorderTest {

    @Mock WorkOrderRepository workOrderRepository;

    @InjectMocks WorkOrderBlockRecorder recorder;

    @Test
    void recordBlocked_setsStatusReasonAndTimestamp() {
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(UUID.randomUUID())
                .status(WorkOrderStatus.DRAFT)
                .plannedQuantity(new BigDecimal("10"))
                .build();
        when(workOrderRepository.findById(workOrder.getWorkOrderId())).thenReturn(Optional.of(workOrder));

        recorder.recordBlocked(workOrder.getWorkOrderId(), "1/2 components short on reservation");

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.BLOCKED);
        assertThat(workOrder.getBlockReason()).isEqualTo("1/2 components short on reservation");
        assertThat(workOrder.getBlockedAt()).isNotNull();
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void recordBlocked_unknownWorkOrder_throwsResourceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(workOrderRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.recordBlocked(missingId, "any reason"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }
}
