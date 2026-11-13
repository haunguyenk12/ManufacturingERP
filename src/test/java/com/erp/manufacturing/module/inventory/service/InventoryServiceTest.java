package com.erp.manufacturing.module.inventory.service;

import static org.mockito.Mockito.mock;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.dto.StockBalanceAggregateResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.StockBalanceAggregateProjection;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService.QualityHold;
import com.erp.manufacturing.module.user.service.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CF-3: the aggregate balance row breaks its quality hold into pending and rejected purchase QC. The breakdown comes
 * from ONE lookup per page (C14/C15); what purchase QC does not explain stays visible as an unclamped remainder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService – aggregate balance quality-hold breakdown (CF-3)")
class InventoryServiceTest {

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    @Mock InventoryMovementService movementService;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockBalanceRepository balanceRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock UserLookupService userLookupService;
    @Mock LotQcOwnershipGuard lotQcOwnershipGuard;
    @Mock PurchaseQcLookupService purchaseQcLookupService;

    InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(movementService, new OrganizationLookupService(mock(CompanyRepository.class), mock(PlantRepository.class), warehouseRepository), balanceRepository, movementRepository,
                new InventoryMapper(), userLookupService, lotQcOwnershipGuard, purchaseQcLookupService);
        when(warehouseRepository.existsById(WAREHOUSE_ID)).thenReturn(true);
    }

    @Test
    void aggregate_looksUpPurchaseQcOncePerPage_withEveryItemOfThePage() {
        UUID itemA = UUID.randomUUID();
        UUID itemB = UUID.randomUUID();
        UUID itemC = UUID.randomUUID();
        page(row(itemA, "15"), row(itemB, "4"), row(itemC, "0"));
        when(purchaseQcLookupService.nonLotQualityHoldByItem(eq(WAREHOUSE_ID), anyCollection())).thenReturn(Map.of(
                itemA, new QualityHold(new BigDecimal("10"), new BigDecimal("5")),
                itemB, new QualityHold(new BigDecimal("1"), BigDecimal.ZERO)));

        List<StockBalanceAggregateResponse> rows = service.listAggregateBalances(WAREHOUSE_ID, null, null, PAGE).content();

        // Exactly one call in total (C15) — not one with the page's items plus one per row.
        verify(purchaseQcLookupService, times(1)).nonLotQualityHoldByItem(any(), anyCollection());
        verify(purchaseQcLookupService).nonLotQualityHoldByItem(WAREHOUSE_ID, List.of(itemA, itemB, itemC));
        assertThat(rows).hasSize(3);
        StockBalanceAggregateResponse a = rows.get(0);
        assertThat(a.pendingPurchaseQcQuantity()).isEqualByComparingTo("10");
        assertThat(a.rejectedPurchaseQcQuantity()).isEqualByComparingTo("5");
        assertThat(a.unclassifiedQualityHoldQuantity()).isEqualByComparingTo("0");
        assertThat(a.qualityBreakdownKnown()).isTrue();
        assertThat(a.qualityHoldQuantity()).isEqualByComparingTo("15");
        StockBalanceAggregateResponse b = rows.get(1);
        assertThat(b.pendingPurchaseQcQuantity()).isEqualByComparingTo("1");
        assertThat(b.unclassifiedQualityHoldQuantity()).isEqualByComparingTo("3");
        assertThat(b.qualityBreakdownKnown()).isFalse();
    }

    @Test
    void aggregate_itemPurchaseQcDoesNotHold_wholeHoldIsUnclassified() {
        UUID held = UUID.randomUUID();
        UUID clean = UUID.randomUUID();
        page(row(held, "6"), row(clean, "0"));
        when(purchaseQcLookupService.nonLotQualityHoldByItem(eq(WAREHOUSE_ID), anyCollection())).thenReturn(Map.of());

        List<StockBalanceAggregateResponse> rows = service.listAggregateBalances(WAREHOUSE_ID, null, null, PAGE).content();

        assertThat(rows.get(0).pendingPurchaseQcQuantity()).isEqualByComparingTo("0");
        assertThat(rows.get(0).rejectedPurchaseQcQuantity()).isEqualByComparingTo("0");
        assertThat(rows.get(0).unclassifiedQualityHoldQuantity()).isEqualByComparingTo("6");
        assertThat(rows.get(0).qualityBreakdownKnown()).isFalse();
        assertThat(rows.get(1).unclassifiedQualityHoldQuantity()).isEqualByComparingTo("0");
        assertThat(rows.get(1).qualityBreakdownKnown()).isTrue();
    }

    /** More explained than held is a data error: it must show as a negative remainder, never be clamped to 0. */
    @Test
    void aggregate_purchaseQcExplainsMoreThanTheHold_remainderIsNegativeNotClamped() {
        UUID item = UUID.randomUUID();
        page(row(item, "3"));
        when(purchaseQcLookupService.nonLotQualityHoldByItem(eq(WAREHOUSE_ID), anyCollection()))
                .thenReturn(Map.of(item, new QualityHold(new BigDecimal("5"), BigDecimal.ZERO)));

        StockBalanceAggregateResponse row = service.listAggregateBalances(WAREHOUSE_ID, null, null, PAGE).content().get(0);

        assertThat(row.unclassifiedQualityHoldQuantity()).isEqualByComparingTo("-2");
        assertThat(row.qualityBreakdownKnown()).isFalse();
    }

    @Test
    void aggregate_emptyPage_asksTheLookupWithNoItems() {
        page();
        when(purchaseQcLookupService.nonLotQualityHoldByItem(eq(WAREHOUSE_ID), anyCollection())).thenReturn(Map.of());

        PageResult<StockBalanceAggregateResponse> result = service.listAggregateBalances(WAREHOUSE_ID, null, null, PAGE);

        assertThat(result.content()).isEmpty();
        verify(purchaseQcLookupService, times(1)).nonLotQualityHoldByItem(WAREHOUSE_ID, List.of());
    }

    private void page(StockBalanceAggregateProjection... rows) {
        Page<StockBalanceAggregateProjection> page = new PageImpl<>(List.of(rows), PAGE, rows.length);
        when(balanceRepository.aggregateBalances(eq(WAREHOUSE_ID), eq(null), eq(null), any(Instant.class), eq(PAGE)))
                .thenReturn(page);
    }

    private static StockBalanceAggregateProjection row(UUID itemId, String qualityHold) {
        return new Row(itemId, new BigDecimal(qualityHold));
    }

    private record Row(UUID itemId, BigDecimal qualityHold) implements StockBalanceAggregateProjection {
        @Override public UUID getItemId() { return itemId; }
        @Override public String getItemCode() { return "I-" + itemId.toString().substring(0, 4); }
        @Override public String getItemName() { return "Item"; }
        @Override public String getUom() { return "KG"; }
        @Override public UUID getWarehouseId() { return WAREHOUSE_ID; }
        @Override public BigDecimal getOnHandQuantity() { return new BigDecimal("25"); }
        @Override public BigDecimal getReservedQuantity() { return BigDecimal.ZERO; }
        @Override public BigDecimal getAvailableQuantity() { return new BigDecimal("25").subtract(qualityHold); }
        @Override public BigDecimal getQualityHoldQuantity() { return qualityHold; }
        @Override public BigDecimal getHeldLotQuantity() { return BigDecimal.ZERO; }
        @Override public BigDecimal getRejectedQuantity() { return BigDecimal.ZERO; }
        @Override public BigDecimal getExpiredQuantity() { return BigDecimal.ZERO; }
        @Override public Long getLotCount() { return 0L; }
        @Override public Instant getUpdatedAt() { return Instant.parse("2026-10-28T00:00:00Z"); }
    }
}
