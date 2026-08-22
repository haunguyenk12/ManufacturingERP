package com.erp.manufacturing.module.inventory.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.domain.WarehouseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The displayed {@code availableQuantity} must respect {@code lot.status}.
 *
 * <p>Regression guard for a defect the frontend hit on 2026-08-14: a lot on QC {@code HOLD} was
 * reported with {@code availableQuantity = 2} by {@code GET /inventory/lots} and
 * {@code GET /inventory/balances}, while the aggregate queries behind MRP and the inventory
 * dashboard reported {@code 0} for the same stock — one system, two answers. B3 forbids issuing or
 * reserving a lot that is not {@code AVAILABLE}, so the read models must say so.
 *
 * <p>{@code onHandQuantity} deliberately keeps the real figure: the stock physically exists, it is
 * only unusable. Hiding it would break stock-taking screens.
 */
@DisplayName("InventoryMapper – lot status gates the displayed availability")
class InventoryMapperTest {

    private final InventoryMapper mapper = new InventoryMapper();

    @Test
    void balanceOfAnAvailableLot_reportsTheRowArithmetic() {
        StockBalance balance = balance(LotStatus.AVAILABLE, "10", "3", "1");

        var response = mapper.toResponse(balance);

        assertThat(response.quantity()).isEqualByComparingTo("10");
        assertThat(response.availableQuantity()).isEqualByComparingTo("6");
    }

    @ParameterizedTest
    @EnumSource(value = LotStatus.class, names = {"HOLD", "REJECTED", "EXPIRED"})
    void balanceOfANonAvailableLot_reportsZeroAvailableButKeepsOnHand(LotStatus status) {
        StockBalance balance = balance(status, "2", "0", "0");

        var response = mapper.toResponse(balance);

        assertThat(response.availableQuantity()).isEqualByComparingTo("0");
        assertThat(response.quantity()).isEqualByComparingTo("2");
    }

    @ParameterizedTest
    @EnumSource(value = LotStatus.class, names = {"HOLD", "REJECTED", "EXPIRED"})
    void lotRowAndLotBalance_reportZeroAvailableForANonAvailableLot(LotStatus status) {
        StockBalance balance = balance(status, "2", "0", "0");

        assertThat(mapper.toLotResponse(balance, null).availableQuantity()).isEqualByComparingTo("0");
        assertThat(mapper.toLotBalanceResponse(balance).availableQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void lotRowOfAnAvailableLot_stillSubtractsReservations() {
        StockBalance balance = balance(LotStatus.AVAILABLE, "10", "4", "0");

        assertThat(mapper.toLotResponse(balance, null).availableQuantity()).isEqualByComparingTo("6");
        assertThat(mapper.toLotBalanceResponse(balance).availableQuantity()).isEqualByComparingTo("6");
    }

    /**
     * Stock with no lot row keeps the row arithmetic including the quality-hold column — that column
     * is the QC carrier for exactly this case (B2), and this path must not change.
     */
    @Test
    void balanceWithoutALot_isUnaffectedAndStillSubtractsQualityHold() {
        StockBalance balance = balance(null, "10", "2", "3");

        var response = mapper.toResponse(balance);

        assertThat(response.lotId()).isNull();
        assertThat(response.qualityHoldQuantity()).isEqualByComparingTo("3");
        assertThat(response.availableQuantity()).isEqualByComparingTo("5");
    }

    /**
     * "Date received" must come from the lot's own stamp. {@code createdAt} is a row audit timestamp
     * that only coincides with it for a lot inserted by its own first receipt, so the fixture sets
     * the two three weeks apart: a mapper wired to the wrong field still returns a plausible date,
     * and only a fixture where they differ can tell the two apart.
     */
    @Test
    void lotRow_reportsTheLotsOwnReceivedAtNotTheRowAuditTimestamp() {
        StockBalance balance = balance(LotStatus.AVAILABLE, "10", "0", "0");
        InventoryLot lot = balance.getLot();
        Instant received = Instant.parse("2026-07-15T06:30:00Z");
        Instant rowCreated = Instant.parse("2026-08-05T09:00:00Z");
        lot.setReceivedAt(received);
        lot.setCreatedAt(rowCreated);
        lot.setUpdatedAt(rowCreated);

        var row = mapper.toLotResponse(balance, null);
        var detail = mapper.toLotDetailResponse(lot, List.of(balance), null);

        assertThat(row.receivedAt()).isEqualTo(received);
        assertThat(row.createdAt()).isEqualTo(rowCreated);
        assertThat(detail.receivedAt()).isEqualTo(received);
        assertThat(detail.createdAt()).isEqualTo(rowCreated);
        // manufactureDate is a legacy alias of the same value, kept only so existing clients survive.
        assertThat(row.manufactureDate()).isEqualTo(received);
        assertThat(detail.manufactureDate()).isEqualTo(received);
    }

    private StockBalance balance(LotStatus lotStatus, String quantity, String reserved, String qualityHold) {
        Company company = Company.builder().companyId(UUID.randomUUID()).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
        Item item = Item.builder().itemId(UUID.randomUUID()).company(company).code("MAT-001").name("Steel")
                .type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(lotStatus != null)
                .status(ItemStatus.ACTIVE).build();
        Warehouse warehouse = Warehouse.builder().warehouseId(UUID.randomUUID())
                .plant(Plant.builder().plantId(UUID.randomUUID()).company(company).code("P1").name("Plant 1")
                        .status(OrganizationStatus.ACTIVE).build())
                .code("WH-01").name("Main").type(WarehouseType.RAW_MATERIAL)
                .status(OrganizationStatus.ACTIVE).build();
        InventoryLot lot = lotStatus == null ? null : InventoryLot.builder()
                .lotId(UUID.randomUUID()).item(item).lotCode("LOT-1").status(lotStatus).build();

        return StockBalance.builder()
                .balanceId(UUID.randomUUID())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .quantity(new BigDecimal(quantity))
                .reservedQuantity(new BigDecimal(reserved))
                .qualityHoldQuantity(new BigDecimal(qualityHold))
                .build();
    }
}
