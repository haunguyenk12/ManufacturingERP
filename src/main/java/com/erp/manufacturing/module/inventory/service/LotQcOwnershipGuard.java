package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CF-1 (invariant B263): a generic quantity adjustment must not change a lot QC still owns — a goods-receipt lot
 * with quantity awaiting purchase QC, a production-output lot with quantity awaiting production QC, or a child lot
 * either QC rejected. The quantity of such a lot belongs to its source document; there is no correction workflow
 * yet, so the adjustment is refused instead of letting it bypass QC.
 *
 * <p>Deliberately narrow: a lot an operator put on {@code HOLD} by hand, outside any receipt, stays adjustable.
 * Lives outside {@link InventoryMovementService} so that service keeps no dependency on purchasing/workorder.
 */
@Component
@RequiredArgsConstructor
public class LotQcOwnershipGuard {

    private final PurchaseQcLookupService purchaseQcLookupService;
    private final LotQcOriginLookupService lotQcOriginLookupService;

    public void ensureAdjustable(InventoryLot lot) {
        if (lot == null) {
            return;
        }
        if (lot.getStatus() == LotStatus.REJECTED
                && (PurchaseQcLookupService.LOT_SOURCE_TYPE.equals(lot.getSourceReferenceType())
                || LotQcOriginLookupService.LOT_SOURCE_TYPE.equals(lot.getSourceReferenceType()))) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Lot was rejected by QC; its quantity belongs to the QC decision and cannot be adjusted");
        }
        if (purchaseQcLookupService.isAwaitingPurchaseQc(lot.getLotId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Lot still has quantity awaiting purchase QC; it cannot be adjusted outside the goods receipt");
        }
        if (lotQcOriginLookupService.requiresQcDispositionBeforeRelease(lot.getLotId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Lot still has quantity awaiting production QC; it cannot be adjusted outside the production receipt");
        }
    }
}
