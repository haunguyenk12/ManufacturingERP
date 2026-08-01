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
import java.util.Collection;
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

    /**
     * Remaining active reservation per component line for one work order, in a single
     * aggregate query. Used by the release gate so readiness never loops per component.
     */
    @Query("""
            select r.componentLine.componentLineId as componentLineId,
                   coalesce(sum(r.quantity - r.consumedQuantity), 0) as quantity
            from MaterialReservation r
            where r.workOrder.workOrderId = :workOrderId
              and r.status = com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus.ACTIVE
            group by r.componentLine.componentLineId
            """)
    List<ComponentQuantityProjection> sumActiveRemainingByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    /**
     * Batch form of {@link #sumActiveRemainingByWorkOrderId} — one query for a whole page of work
     * orders instead of one per row (rule C14). Added in F9 so
     * {@code WorkOrderComponentLineResponse.reservedQuantity} (spec §3.3 "Requirement") can be filled
     * without turning {@code GET /work-orders} into N+1.
     *
     * <p>Deliberately returns the <em>same</em> {@link ComponentQuantityProjection} as the
     * single-work-order query and does <b>not</b> carry a {@code workOrderId}:
     * {@code componentLineId} is globally unique, so one flat map keyed by it is enough for the whole
     * page.
     *
     * <p>🔴 The {@code status = ACTIVE} predicate is the part a mocked repository cannot check — it
     * would simply return whatever the test handed it. Counting released or consumed reservations
     * here would report material as held that is not, which is why this is pinned by
     * {@code MaterialReservationRepositoryIT} (rule R7).
     */
    @Query("""
            select r.componentLine.componentLineId as componentLineId,
                   coalesce(sum(r.quantity - r.consumedQuantity), 0) as quantity
            from MaterialReservation r
            where r.workOrder.workOrderId in :workOrderIds
              and r.status = com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus.ACTIVE
            group by r.componentLine.componentLineId
            """)
    List<ComponentQuantityProjection> sumActiveRemainingByWorkOrderIds(
            @Param("workOrderIds") Collection<UUID> workOrderIds);
}
