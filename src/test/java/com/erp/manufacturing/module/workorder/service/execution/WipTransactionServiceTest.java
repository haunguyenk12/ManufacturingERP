package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.workorder.domain.WipTransaction;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WipTransactionService tests")
class WipTransactionServiceTest {

    @Mock WipTransactionRepository wipTransactionRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock WorkOrderOperationRepository operationRepository;

    WipTransactionService service;

    @BeforeEach
    void setUp() {
        WorkOrderExecutionSupport support = new WorkOrderExecutionSupport(
                workOrderRepository, organizationLookupService, inventoryAvailabilityService);
        service = new WipTransactionService(
                wipTransactionRepository, operationRepository, support, new ManufacturingExecutionMapper());
    }

    @Test
    void record_scrapTransaction_success() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = WorkOrder.builder().workOrderId(workOrderId).status(WorkOrderStatus.RELEASED).build();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(wipTransactionRepository.save(any(WipTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.SCRAP_REPORTED, null, "S1", new BigDecimal("2"), null, "Scrap"));

        assertThat(response.transactionType()).isEqualTo(WipTransactionType.SCRAP_REPORTED.name());
        assertThat(response.quantity()).isEqualByComparingTo("2");
    }

    @Test
    void record_startTransactionFromApi_fails() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId))
                .thenReturn(Optional.of(WorkOrder.builder().workOrderId(workOrderId).build()));

        assertThatThrownBy(() -> service.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.START, null, null, BigDecimal.ONE, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(wipTransactionRepository, never()).save(any());
    }
}
