package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @EntityGraph(attributePaths = {"purchaseOrder", "item", "purchaseRequisitionLine"})
    List<PurchaseOrderLine> findByPurchaseOrderPurchaseOrderIdAndPurchaseOrderLineIdIn(
            UUID purchaseOrderId, Collection<UUID> purchaseOrderLineIds);

    /** G7b: the lines of a page of receiving candidates, one query (C13/C14). */
    @EntityGraph(attributePaths = {"purchaseOrder", "item"})
    List<PurchaseOrderLine> findByPurchaseOrderPurchaseOrderIdIn(Collection<UUID> purchaseOrderIds);
}
