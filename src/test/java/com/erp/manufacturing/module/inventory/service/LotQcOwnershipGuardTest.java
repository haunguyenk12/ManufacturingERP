package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CF-1 (B263): which lots a generic adjustment may not touch. The two lookups belong to other modules
 * (purchasing, workorder), so they are mocked; the decision itself is the guard's.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LotQcOwnershipGuard tests")
class LotQcOwnershipGuardTest {

    @Mock PurchaseQcLookupService purchaseQcLookupService;
    @Mock LotQcOriginLookupService lotQcOriginLookupService;

    LotQcOwnershipGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LotQcOwnershipGuard(purchaseQcLookupService, lotQcOriginLookupService);
    }

    @Test
    void nullLot_nonTrackedItem_isANoOpWithoutAnyLookup() {
        assertThatCode(() -> guard.ensureAdjustable(null)).doesNotThrowAnyException();

        verifyNoInteractions(purchaseQcLookupService, lotQcOriginLookupService);
    }

    @Test
    void lotAwaitingPurchaseQc_isRefusedWithStateConflict() {
        InventoryLot lot = lot(LotStatus.HOLD, null);
        when(purchaseQcLookupService.isAwaitingPurchaseQc(lot.getLotId())).thenReturn(true);

        assertStateConflict(lot);
    }

    @Test
    void lotAwaitingProductionQc_isRefusedWithStateConflict() {
        InventoryLot lot = lot(LotStatus.HOLD, null);
        when(lotQcOriginLookupService.requiresQcDispositionBeforeRelease(lot.getLotId())).thenReturn(true);

        assertStateConflict(lot);
        verify(purchaseQcLookupService).isAwaitingPurchaseQc(lot.getLotId());
    }

    @Test
    void childLotRejectedByPurchaseQc_isRefusedWithStateConflict() {
        assertStateConflict(lot(LotStatus.REJECTED, PurchaseQcLookupService.LOT_SOURCE_TYPE));
    }

    @Test
    void childLotRejectedByProductionQc_isRefusedWithStateConflict() {
        assertStateConflict(lot(LotStatus.REJECTED, LotQcOriginLookupService.LOT_SOURCE_TYPE));
    }

    /** A lot an operator put on HOLD by hand, outside any receipt, stays adjustable (narrow by design). */
    @Test
    void lotHeldByHand_withoutAnyReceipt_isAdjustable() {
        InventoryLot lot = lot(LotStatus.HOLD, null);

        assertThatCode(() -> guard.ensureAdjustable(lot)).doesNotThrowAnyException();

        verify(purchaseQcLookupService).isAwaitingPurchaseQc(lot.getLotId());
        verify(lotQcOriginLookupService).requiresQcDispositionBeforeRelease(lot.getLotId());
    }

    @Test
    void rejectedLot_withoutAQcSourceType_isAdjustable() {
        assertThatCode(() -> guard.ensureAdjustable(lot(LotStatus.REJECTED, null))).doesNotThrowAnyException();
        assertThatCode(() -> guard.ensureAdjustable(lot(LotStatus.REJECTED, "MANUAL"))).doesNotThrowAnyException();
    }

    /** The accepted child of a QC decision carries the QC source type but is AVAILABLE: it is ordinary stock. */
    @Test
    void acceptedChildLot_ofAPurchaseQcDecision_isAdjustable() {
        assertThatCode(() -> guard.ensureAdjustable(lot(LotStatus.AVAILABLE, PurchaseQcLookupService.LOT_SOURCE_TYPE)))
                .doesNotThrowAnyException();
    }

    private void assertStateConflict(InventoryLot lot) {
        assertThatThrownBy(() -> guard.ensureAdjustable(lot))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    private static InventoryLot lot(LotStatus status, String sourceReferenceType) {
        return InventoryLot.builder()
                .lotId(UUID.randomUUID())
                .lotCode("LOT-1")
                .status(status)
                .sourceReferenceType(sourceReferenceType)
                .build();
    }
}
