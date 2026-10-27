package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.workorder.domain.ProductionCostAdjustment;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostCheck;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostSnapshot;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.PartAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ReceiptAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** G11c: how a failed checklist is answered (FE §4) and which value-only entries a close writes (FE §3.6, E9). */
class ProductionCostCloseServiceTest {

    private static final List<String> CODES = List.of("STATE", "OPERATIONS", "ISSUES", "MATERIAL", "RESERVATIONS",
            "RECEIPTS", "QC", "BASELINE", "COST", "VALUATION", "PREVIEW");

    @Test
    void allChecksPass_noError() {
        ProductionCostCloseService.ensureChecks(checks());
    }

    @Test
    void wrongStatusWins_overEveryOtherFailure() {
        assertCode(checks("STATE", "QC", "COST", "VALUATION"), BusinessErrorCode.STATE_CONFLICT);
    }

    @Test
    void openShopFloorWork_isCloseBlocked() {
        assertCode(checks("QC", "COST"), BusinessErrorCode.CLOSE_BLOCKED);
    }

    @Test
    void missingCostData_isCostDataIncomplete() {
        assertCode(checks("BASELINE", "VALUATION"), BusinessErrorCode.COST_DATA_INCOMPLETE);
    }

    @Test
    void mixedOutputPool_isValuationUnreconciled() {
        assertCode(checks("VALUATION"), BusinessErrorCode.VALUATION_UNRECONCILED);
    }

    @Test
    void stalePreviewAlone_isCostSourceStale() {
        assertCode(checks("PREVIEW"), BusinessErrorCode.COST_SOURCE_STALE);
    }

    /**
     * F1 at close: QC_A delivered whole (one COGS entry), QC_B delivered whole, QC_R still on hand (one stock entry), PR2
     * delivered whole — quantity 0 everywhere, and the entries add back to the actual total (FE A31).
     */
    @Test
    void adjustments_f1_oneEntryPerDeliveryAndOnePerPartLeftOnHand() {
        UUID dn1 = UUID.randomUUID();
        UUID dn2 = UUID.randomUUID();
        ReceiptAllocation pr1 = new ReceiptAllocation(UUID.randomUUID(), "PR1", UUID.randomUUID(), null, new BigDecimal("5"),
                new BigDecimal("19166.666667"), List.of(
                part("ACCEPTED", "3", "11500", "0", "0", delivery(dn1, "3", "11500")),
                part("ACCEPTED", "1", "3833.333334", "0", "0", delivery(dn2, "1", "3833.333334")),
                part("REJECTED", "1", "3833.333333", "1", "3833.333333")));
        ReceiptAllocation pr2 = new ReceiptAllocation(UUID.randomUUID(), "PR2", UUID.randomUUID(), null, new BigDecimal("4"),
                new BigDecimal("15333.333333"), List.of(
                part("ACCEPTED", "4", "15333.333333", "0", "0", delivery(dn2, "4", "15333.333333"))));
        CostSnapshot snapshot = new CostSnapshot(null, "VND", null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(pr1, pr2), List.of());

        List<ProductionCostAdjustment> entries = ProductionCostCloseService.adjustments(workOrder(), UUID.randomUUID(),
                snapshot, "Reconciled");

        assertThat(entries).hasSize(4);
        assertThat(entries).allSatisfy(entry -> assertThat(entry.getQuantity()).isEqualByComparingTo("0"));
        List<ProductionCostAdjustment> stock = entries.stream().filter(e -> e.getDeliveryLineId() == null).toList();
        assertThat(stock).singleElement().satisfies(entry -> {
            assertThat(entry.getStockValueDelta()).isEqualByComparingTo("3833.333333");
            assertThat(entry.getStockValueBefore()).isNull();
            assertThat(entry.getQuality()).isEqualTo("REJECTED");
        });
        BigDecimal cogs = entries.stream().filter(e -> e.getDeliveryLineId() != null)
                .peek(e -> assertThat(e.getStockValueDelta()).isEqualByComparingTo("0"))
                .map(ProductionCostAdjustment::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cogs).isEqualByComparingTo("30666.666667");
        assertThat(entries).extracting(ProductionCostAdjustment::getLineNo).containsExactly(1, 2, 3, 4);
    }

    private static PartAllocation part(String quality, String quantity, String amount, String remainingQuantity,
                                       String remainingValue, DeliveryAllocation... deliveries) {
        return new PartAllocation(quality, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(quantity),
                new BigDecimal(amount), new BigDecimal(remainingQuantity), new BigDecimal(remainingValue), List.of(deliveries));
    }

    private static DeliveryAllocation delivery(UUID deliveryId, String quantity, String amount) {
        return new DeliveryAllocation(deliveryId, "DN", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(quantity),
                new BigDecimal(amount));
    }

    private static WorkOrder workOrder() {
        return WorkOrder.builder().workOrderId(UUID.randomUUID())
                .company(Company.builder().companyId(UUID.randomUUID()).build())
                .plant(Plant.builder().plantId(UUID.randomUUID()).build())
                .productItem(Item.builder().itemId(UUID.randomUUID()).build()).build();
    }

    private static List<CostCheck> checks(String... failing) {
        List<String> failed = List.of(failing);
        List<CostCheck> checks = new ArrayList<>();
        for (String code : CODES) {
            checks.add(new CostCheck(code, !failed.contains(code), failed.contains(code) ? List.of("X") : List.of(), null));
        }
        return checks;
    }

    private static void assertCode(List<CostCheck> checks, BusinessErrorCode code) {
        assertThatThrownBy(() -> ProductionCostCloseService.ensureChecks(checks)).isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
