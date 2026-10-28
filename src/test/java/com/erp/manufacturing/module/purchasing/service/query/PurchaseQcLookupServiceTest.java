package com.erp.manufacturing.module.purchasing.service.query;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseQcHoldProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** CF-3: the non-lot purchase-QC hold lookup the aggregate balance page calls once. */
@ExtendWith(MockitoExtension.class)
class PurchaseQcLookupServiceTest {

    @Mock GoodsReceiptLineRepository goodsReceiptLineRepository;
    @InjectMocks PurchaseQcLookupService service;

    @Test
    void nonLotQualityHoldByItem_noItems_returnsEmptyWithoutQuerying() {
        assertThat(service.nonLotQualityHoldByItem(UUID.randomUUID(), List.of())).isEmpty();

        verifyNoInteractions(goodsReceiptLineRepository);
    }

    @Test
    void nonLotQualityHoldByItem_asksForTheWarehouseAndPostedReceipts_keysByItem() {
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseQcHoldProjection row = mock(PurchaseQcHoldProjection.class);
        when(row.getItemId()).thenReturn(itemId);
        when(row.getPendingQuantity()).thenReturn(new BigDecimal("10"));
        when(row.getRejectedQuantity()).thenReturn(new BigDecimal("5"));
        when(goodsReceiptLineRepository.sumNonLotQualityHold(List.of(itemId), List.of(warehouseId), GoodsReceiptStatus.POSTED))
                .thenReturn(List.of(row));

        Map<UUID, PurchaseQcLookupService.QualityHold> holds = service.nonLotQualityHoldByItem(warehouseId, List.of(itemId));

        assertThat(holds).containsOnlyKeys(itemId);
        assertThat(holds.get(itemId).pendingQuantity()).isEqualByComparingTo("10");
        assertThat(holds.get(itemId).rejectedQuantity()).isEqualByComparingTo("5");
        verify(goodsReceiptLineRepository).sumNonLotQualityHold(List.of(itemId), List.of(warehouseId), GoodsReceiptStatus.POSTED);
    }
}
