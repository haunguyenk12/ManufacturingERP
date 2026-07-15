package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    Optional<GoodsReceipt> findByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(UUID purchaseOrderId, String idempotencyKey);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "warehouse",
            "lines",
            "lines.purchaseOrderLine",
            "lines.item",
            "lines.lot",
            "lines.stockMovement"
    })
    Optional<GoodsReceipt> findWithDetailsByPurchaseOrderPurchaseOrderIdAndIdempotencyKey(
            UUID purchaseOrderId, String idempotencyKey);

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    Page<GoodsReceipt> findByPurchaseOrderPurchaseOrderId(UUID purchaseOrderId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "warehouse",
            "lines",
            "lines.purchaseOrderLine",
            "lines.item",
            "lines.lot",
            "lines.stockMovement"
    })
    Optional<GoodsReceipt> findWithDetailsByGoodsReceiptId(UUID goodsReceiptId);
}
