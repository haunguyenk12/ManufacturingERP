package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.module.inventory.domain.MovementType;
import com.erp.manufacturing.module.inventory.service.StockPoolLookupService.PoolMovement;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** G11b: the opaque source version (FE Q31, R18) and what counts as the work order's own pool movement (D3). */
class ProductionCostSourceReaderTest {

    private static final UUID WO = UUID.randomUUID();

    @Test
    void sameSources_sameVersion_withoutAnyAmount() {
        String first = ProductionCostSourceReader.fingerprint(workOrder(3L), inputs(List.of()), List.of());
        String second = ProductionCostSourceReader.fingerprint(workOrder(3L), inputs(List.of()), List.of());

        assertThat(first).isEqualTo(second).startsWith("cs1-").hasSize(36);
    }

    /** FE A20: a delivery out of the output pool after the preview makes it stale. */
    @Test
    void aNewDelivery_changesTheVersion() {
        CostInputs.DeliveryLine line = new CostInputs.DeliveryLine(UUID.randomUUID(), "DN", UUID.randomUUID(), null,
                BigDecimal.ONE, UUID.randomUUID());

        assertThat(ProductionCostSourceReader.fingerprint(workOrder(3L), inputs(List.of(line)), List.of()))
                .isNotEqualTo(ProductionCostSourceReader.fingerprint(workOrder(3L), inputs(List.of()), List.of()));
    }

    @Test
    void aNewWorkOrderVersion_changesTheVersion() {
        assertThat(ProductionCostSourceReader.fingerprint(workOrder(4L), inputs(List.of()), List.of()))
                .isNotEqualTo(ProductionCostSourceReader.fingerprint(workOrder(3L), inputs(List.of()), List.of()));
    }

    @Test
    void ownMovement_receiptQcAndDeliveryOfThisOrderOnly() {
        UUID decision = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        Set<String> decisions = Set.of(decision.toString());
        Set<String> deliveries = Set.of(delivery.toString());

        assertThat(ProductionCostSourceReader.ownMovement(move("WORK_ORDER", WO), WO.toString(), decisions, deliveries)).isTrue();
        assertThat(ProductionCostSourceReader.ownMovement(move("PRODUCTION_QC_DECISION", decision), WO.toString(), decisions, deliveries)).isTrue();
        assertThat(ProductionCostSourceReader.ownMovement(move("DELIVERY", delivery), WO.toString(), decisions, deliveries)).isTrue();
        assertThat(ProductionCostSourceReader.ownMovement(move("WORK_ORDER", UUID.randomUUID()), WO.toString(), decisions, deliveries)).isFalse();
        assertThat(ProductionCostSourceReader.ownMovement(move("STOCK_TRANSFER", UUID.randomUUID()), WO.toString(), decisions, deliveries)).isFalse();
    }

    private static PoolMovement move(String referenceType, UUID referenceId) {
        return new PoolMovement(UUID.randomUUID(), MovementType.RECEIVE, referenceType, referenceId.toString(),
                BigDecimal.ONE, null, Instant.now());
    }

    private static WorkOrder workOrder(Long version) {
        WorkOrder workOrder = WorkOrder.builder().workOrderId(WO).status(WorkOrderStatus.COMPLETED).build();
        workOrder.setVersion(version);
        return workOrder;
    }

    private static CostInputs inputs(List<CostInputs.DeliveryLine> deliveries) {
        return new CostInputs(WO, WorkOrderStatus.COMPLETED, true, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, null, List.of(), List.of(), List.of(), List.of(), List.of(), false, false,
                BigDecimal.ZERO, deliveries, 0);
    }
}
