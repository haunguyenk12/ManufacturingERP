package com.erp.manufacturing.module.purchasing.service.query;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptStatus;
import com.erp.manufacturing.module.purchasing.repository.GoodsReceiptLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** CF-3: pending and rejected purchase-QC quantity still held on non-lot stock, one row per item and warehouse. */
    public record QualityHold(BigDecimal pendingQuantity, BigDecimal rejectedQuantity) {
        public static final QualityHold NONE = new QualityHold(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** One aggregate query for a whole page of rows (C14); a key absent from the map has nothing held by purchase QC. */
    @Transactional(readOnly = true)
    public Map<UUID, QualityHold> nonLotQualityHoldByItem(UUID warehouseId, Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, QualityHold> holds = new HashMap<>();
        goodsReceiptLineRepository.sumNonLotQualityHold(itemIds, List.of(warehouseId), GoodsReceiptStatus.POSTED)
                .forEach(row -> holds.put(row.getItemId(),
                        new QualityHold(row.getPendingQuantity(), row.getRejectedQuantity())));
        return holds;
    }

    /** CF-4: one goods receipt line holding pending purchase-QC quantity, as MRP shows it (no price, no supplier contact). */
    public record PendingQcLine(UUID goodsReceiptId, UUID goodsReceiptLineId, String receiptCode, Long receiptVersion,
                                UUID itemId, UUID warehouseId, String uomCode, BigDecimal pendingQuantity) {
    }

    /** One query for a whole run (C14): every pending line of the given items in the given warehouses. */
    @Transactional(readOnly = true)
    public List<PendingQcLine> pendingQualityLines(Collection<UUID> itemIds, Collection<UUID> warehouseIds) {
        if (itemIds.isEmpty() || warehouseIds.isEmpty()) {
            return List.of();
        }
        return goodsReceiptLineRepository.findPendingQualityLines(itemIds, warehouseIds, GoodsReceiptStatus.POSTED)
                .stream()
                .map(row -> new PendingQcLine(row.getGoodsReceiptId(), row.getGoodsReceiptLineId(), row.getReceiptNo(),
                        row.getReceiptVersion(), row.getItemId(), row.getWarehouseId(), row.getUomCode(),
                        row.getPendingQuantity()))
                .toList();
    }
}
