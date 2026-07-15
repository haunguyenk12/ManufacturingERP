package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.repository.*;
import com.erp.manufacturing.module.workorder.service.query.WorkOrderVarianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderVarianceService tests")
class WorkOrderVarianceServiceTest {

    @Mock MaterialIssueLineRepository materialIssueLineRepository;
    @Mock ProductionReceiptLineRepository productionReceiptLineRepository;
    @Mock WipTransactionRepository wipTransactionRepository;
    @Mock WorkOrderRepository workOrderRepository;

    WorkOrderVarianceService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderVarianceService(
                materialIssueLineRepository,
                productionReceiptLineRepository,
                wipTransactionRepository,
                workOrderRepository);
    }

    @Test
    void getVariance_calculatesMaterialOutputAndWipSummary() {
        UUID workOrderId = UUID.randomUUID();
        UUID componentLineId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), "FG-100", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), "RM-001", ItemType.RAW_MATERIAL);
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(workOrderId)
                .workOrderNo("WO-001")
                .productItem(product)
                .plannedQuantity(new BigDecimal("10"))
                .status(WorkOrderStatus.COMPLETED)
                .componentLines(new ArrayList<>())
                .build();
        workOrder.getComponentLines().add(WorkOrderComponentLine.builder()
                .componentLineId(componentLineId)
                .componentItem(component)
                .bomLine(BomLine.builder().lineId(UUID.randomUUID()).build())
                .lineNo(10)
                .requiredQuantity(new BigDecimal("12"))
                .issuedQuantity(new BigDecimal("13"))
                .build());

        when(workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)).thenReturn(Optional.of(workOrder));
        when(materialIssueLineRepository.sumIssuedByWorkOrder(workOrderId))
                .thenReturn(List.of(new TestQuantity(componentLineId, new BigDecimal("13"))));
        when(productionReceiptLineRepository.sumReceivedQuantityByWorkOrder(workOrderId))
                .thenReturn(new BigDecimal("9"));
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.SCRAP_REPORTED))
                .thenReturn(new BigDecimal("1"));
        when(wipTransactionRepository.sumQuantityByWorkOrderAndType(workOrderId, WipTransactionType.REWORK_REPORTED))
                .thenReturn(new BigDecimal("2"));

        var response = service.getVariance(workOrderId);

        assertThat(response.materialLines().get(0).varianceQuantity()).isEqualByComparingTo("1");
        assertThat(response.materialLines().get(0).status()).isEqualTo(VarianceStatus.OVER_ISSUED.name());
        assertThat(response.outputVariance().varianceQuantity()).isEqualByComparingTo("-1");
        assertThat(response.wipSummary().scrapQuantity()).isEqualByComparingTo("1");
        assertThat(response.wipSummary().reworkQuantity()).isEqualByComparingTo("2");
    }

    private record TestQuantity(UUID componentLineId, BigDecimal quantity) implements ComponentQuantityProjection {
        @Override public UUID getComponentLineId() { return componentLineId; }
        @Override public BigDecimal getQuantity() { return quantity; }
    }

    private Item item(UUID itemId, String code, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(Company.builder()
                        .companyId(UUID.randomUUID())
                        .code("ACME")
                        .name("ACME")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }
}
