package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    boolean existsByPlantPlantIdAndWorkOrderNo(UUID plantId, String workOrderNo);

    @Query("""
            select w
            from WorkOrder w
            where w.plant.plantId = :plantId
              and (:status is null or w.status = :status)
              and (:productItemId is null or w.productItem.itemId = :productItemId)
            """)
    Page<WorkOrder> search(@Param("plantId") UUID plantId,
                           @Param("status") WorkOrderStatus status,
                           @Param("productItemId") UUID productItemId,
                           Pageable pageable);

    @EntityGraph(attributePaths = {
            "company",
            "plant",
            "productItem",
            "bom",
            "outputWarehouse",
            "componentLines",
            "componentLines.bomLine",
            "componentLines.componentItem"
    })
    Optional<WorkOrder> findWithDetailsByWorkOrderId(UUID workOrderId);

    @Query("""
            select w.productItem.itemId as itemId,
                   coalesce(sum(w.plannedQuantity - w.completedQuantity), 0) as openSupplyQuantity
            from WorkOrder w
            where w.company.companyId = :companyId
              and w.plant.plantId = :plantId
              and w.productItem.itemId in :itemIds
              and w.outputWarehouse.warehouseId in :warehouseIds
              and w.status in :statuses
            group by w.productItem.itemId
            """)
    List<WorkOrderSupplyProjection> aggregateOpenSupply(
            @Param("companyId") UUID companyId,
            @Param("plantId") UUID plantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("itemIds") Collection<UUID> itemIds,
            @Param("statuses") Collection<WorkOrderStatus> statuses);
}
