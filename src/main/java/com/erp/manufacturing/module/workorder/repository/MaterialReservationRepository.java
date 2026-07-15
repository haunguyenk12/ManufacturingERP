package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialReservationRepository extends JpaRepository<MaterialReservation, UUID> {

    @EntityGraph(attributePaths = {
            "workOrder",
            "componentLine",
            "item",
            "warehouse",
            "lot"
    })
    Optional<MaterialReservation> findWithDetailsByReservationId(UUID reservationId);

    @EntityGraph(attributePaths = {"componentLine", "item", "warehouse", "lot"})
    Page<MaterialReservation> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);

    @EntityGraph(attributePaths = {"componentLine", "item", "warehouse", "lot"})
    List<MaterialReservation> findByWorkOrderWorkOrderIdAndStatus(UUID workOrderId, MaterialReservationStatus status);

    @Query("""
            select coalesce(sum(r.quantity - r.consumedQuantity), 0)
            from MaterialReservation r
            where r.componentLine.componentLineId = :componentLineId
              and r.status = com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus.ACTIVE
            """)
    BigDecimal sumActiveRemainingByComponentLineId(@Param("componentLineId") UUID componentLineId);
}
