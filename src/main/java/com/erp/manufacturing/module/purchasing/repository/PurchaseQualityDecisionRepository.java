package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseQualityDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseQualityDecisionRepository extends JpaRepository<PurchaseQualityDecision, UUID> {

    /** Newest first, then id, so equal timestamps never shuffle between pages (FE §3.5). */
    @EntityGraph(attributePaths = {"goodsReceiptLine", "goodsReceiptLine.item", "acceptedLot", "rejectedLot"})
    @Query(value = """
            select d from PurchaseQualityDecision d
            where d.goodsReceipt.goodsReceiptId = :goodsReceiptId
            order by d.decidedAt desc, d.decisionId desc
            """,
            countQuery = """
            select count(d) from PurchaseQualityDecision d
            where d.goodsReceipt.goodsReceiptId = :goodsReceiptId
            """)
    Page<PurchaseQualityDecision> findByReceipt(@Param("goodsReceiptId") UUID goodsReceiptId, Pageable pageable);

    @EntityGraph(attributePaths = {"goodsReceipt", "goodsReceiptLine", "goodsReceiptLine.item", "acceptedLot",
            "rejectedLot", "acceptedMovement", "rejectedMovement"})
    Optional<PurchaseQualityDecision> findWithDetailsByDecisionId(UUID decisionId);
}
