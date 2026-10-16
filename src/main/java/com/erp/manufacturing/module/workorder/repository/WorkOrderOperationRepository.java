package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation, UUID> {

    List<WorkOrderOperation> findByWorkOrderWorkOrderIdOrderBySequenceAsc(UUID workOrderId);

    Optional<WorkOrderOperation> findByWorkOrderOperationIdAndWorkOrderWorkOrderId(UUID workOrderOperationId,
                                                                                  UUID workOrderId);

    /** G5: a work order that is not finished still has an operation on this work center. */
    boolean existsByWorkCenterWorkCenterIdAndWorkOrderStatusIn(UUID workCenterId, Collection<WorkOrderStatus> statuses);

    /**
     * Capacity Board rows: every scheduled operation whose window overlaps {@code [from, to)}. G5 (D9)
     * replaced the C2-8 rule "operations that START in the range" — an operation that began before the
     * range and runs into it is load inside the range, and used to be invisible. The bounds are the
     * plant-local period already converted to instants by the caller, so no timezone function is
     * needed here any more. Operations with no {@code workCenter} (legacy routing rows predating C2-6)
     * or no schedule yet (work order never released) cannot be placed on the board at all.
     *
     * <p>{@code @EntityGraph} on to-one associations only — safe with pagination per rule C13.
     */
    @EntityGraph(attributePaths = {"workOrder", "workOrder.plant", "workCenter", "workCenter.workCalendar"})
    @Query("""
            select o
            from WorkOrderOperation o
            where o.workOrder.plant.plantId = :plantId
              and o.workCenter is not null
              and o.plannedStartAt is not null
              and o.plannedEndAt is not null
              and o.plannedStartAt < :to
              and o.plannedEndAt > :from
              and (:workCenterId is null or o.workCenter.workCenterId = :workCenterId)
              and (:status is null or o.workOrder.status = :status)
            """)
    Page<WorkOrderOperation> searchCapacityBoard(@Param("plantId") UUID plantId,
                                                  @Param("workCenterId") UUID workCenterId,
                                                  @Param("status") WorkOrderStatus status,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  Pageable pageable);

    /**
     * The load side (G5): every scheduled operation of a load-carrying work order on the plant whose
     * window overlaps {@code [from, to)}, in one query (rule C14). Allocation to days or periods happens
     * in {@code CapacityLoadCalculator} — it needs the work centers' calendars, which SQL does not have.
     */
    @EntityGraph(attributePaths = {"workOrder", "workCenter", "workCenter.workCalendar"})
    @Query("""
            select o
            from WorkOrderOperation o
            where o.workOrder.plant.plantId = :plantId
              and o.workCenter is not null
              and o.plannedStartAt is not null
              and o.plannedEndAt is not null
              and o.workOrder.status in :loadStatuses
              and o.plannedStartAt < :to
              and o.plannedEndAt > :from
            """)
    List<WorkOrderOperation> findScheduledOverlapping(@Param("plantId") UUID plantId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("loadStatuses") List<WorkOrderStatus> loadStatuses);
}
