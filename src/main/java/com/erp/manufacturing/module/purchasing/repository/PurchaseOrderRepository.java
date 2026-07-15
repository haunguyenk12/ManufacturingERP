package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseOrder;
import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    boolean existsByCompanyCompanyIdAndPurchaseOrderNo(UUID companyId, String purchaseOrderNo);

    @EntityGraph(attributePaths = {"company", "plant", "warehouse", "supplier"})
    @Query("""
            select p
            from PurchaseOrder p
            where p.company.companyId = :companyId
              and p.plant.plantId = :plantId
              and (:warehouseId is null or p.warehouse.warehouseId = :warehouseId)
              and (:supplierId is null or p.supplier.supplierId = :supplierId)
              and (:status is null or p.status = :status)
            """)
    Page<PurchaseOrder> search(@Param("companyId") UUID companyId,
                               @Param("plantId") UUID plantId,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("supplierId") UUID supplierId,
                               @Param("status") PurchaseOrderStatus status,
                               Pageable pageable);

    @EntityGraph(attributePaths = {
            "company",
            "plant",
            "warehouse",
            "supplier",
            "sourceRequisition",
            "lines",
            "lines.item",
            "lines.purchaseRequisitionLine"
    })
    Optional<PurchaseOrder> findWithDetailsByPurchaseOrderId(UUID purchaseOrderId);
}
