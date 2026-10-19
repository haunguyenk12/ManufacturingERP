package com.erp.manufacturing.module.purchasing.service.query;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * G7c entry point for other modules (rule C7): whether a lot is still owned by purchase QC. Inventory
 * uses it so a generic lot status change cannot release a received lot past QC (R13) — the purchase
 * counterpart of {@code LotQcOriginLookupService} for production lots. No {@code @PreAuthorize}: the
 * caller has already been authorised on the lot.
 */
@Service
@RequiredArgsConstructor
public class PurchaseQcLookupService {

    /** {@code inventory_lots.source_reference_type} of a child lot split off by a purchase QC decision. */
    public static final String LOT_SOURCE_TYPE = "PURCHASE_QC_DECISION";

    private final GoodsReceiptLineRepository goodsReceiptLineRepository;

    @Transactional(readOnly = true)
    public boolean isAwaitingPurchaseQc(UUID lotId) {
        return lotId != null && goodsReceiptLineRepository.existsPendingQualityForLot(lotId, GoodsReceiptStatus.POSTED);
    }
}
