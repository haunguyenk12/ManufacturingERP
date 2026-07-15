package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseRequisition;
import com.erp.manufacturing.module.purchasing.domain.PurchaseRequisitionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, UUID> {

    boolean existsByCompanyCompanyIdAndRequisitionNo(UUID companyId, String requisitionNo);

    @EntityGraph(attributePaths = {"company", "plant", "warehouse"})
    @Query("""
            select r
            from PurchaseRequisition r
            where r.company.companyId = :companyId
              and r.plant.plantId = :plantId
              and (:warehouseId is null or r.warehouse.warehouseId = :warehouseId)
              and (:status is null or r.status = :status)
            """)
    Page<PurchaseRequisition> search(@Param("companyId") UUID companyId,
                                     @Param("plantId") UUID plantId,
                                     @Param("warehouseId") UUID warehouseId,
                                     @Param("status") PurchaseRequisitionStatus status,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {
            "company",
            "plant",
            "warehouse",
            "lines",
            "lines.item",
            "lines.supplier"
    })
    Optional<PurchaseRequisition> findWithDetailsByPurchaseRequisitionId(UUID purchaseRequisitionId);
}
