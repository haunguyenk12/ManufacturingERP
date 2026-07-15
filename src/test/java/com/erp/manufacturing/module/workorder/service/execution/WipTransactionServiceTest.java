package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.workorder.domain.WipTransaction;
import com.erp.manufacturing.module.workorder.domain.WipTransactionType;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.dto.execution.WipTransactionRequest;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.WipTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WipTransactionService tests")
class WipTransactionServiceTest {

    @Mock WipTransactionRepository wipTransactionRepository;
    @Mock WorkOrderExecutionSupport support;

    WipTransactionService service;

    @BeforeEach
    void setUp() {
        service = new WipTransactionService(wipTransactionRepository, support, new ManufacturingExecutionMapper());
    }

    @Test
    void record_scrapTransaction_success() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = WorkOrder.builder().workOrderId(workOrderId).build();
        when(support.findWorkOrder(workOrderId)).thenReturn(workOrder);
        when(support.requirePositive(new BigDecimal("2"), "WIP transaction quantity"))
                .thenReturn(new BigDecimal("2"));
        when(support.trimToNull("S1")).thenReturn("S1");
        when(support.trimToNull("Scrap")).thenReturn("Scrap");
        when(wipTransactionRepository.save(any(WipTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.SCRAP_REPORTED, "S1", new BigDecimal("2"), null, "Scrap"));

        assertThat(response.transactionType()).isEqualTo(WipTransactionType.SCRAP_REPORTED.name());
        assertThat(response.quantity()).isEqualByComparingTo("2");
    }

    @Test
    void record_startTransactionFromApi_fails() {
        UUID workOrderId = UUID.randomUUID();
        when(support.findWorkOrder(workOrderId)).thenReturn(WorkOrder.builder().workOrderId(workOrderId).build());

        assertThatThrownBy(() -> service.record(workOrderId, new WipTransactionRequest(
                WipTransactionType.START, null, BigDecimal.ONE, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(wipTransactionRepository, never()).save(any());
    }
}
