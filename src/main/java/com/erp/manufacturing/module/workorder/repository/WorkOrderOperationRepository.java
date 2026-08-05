package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderOperationRepository extends JpaRepository<WorkOrderOperation, UUID> {

    List<WorkOrderOperation> findByWorkOrderWorkOrderIdOrderBySequenceAsc(UUID workOrderId);

    Optional<WorkOrderOperation> findByWorkOrderOperationIdAndWorkOrderWorkOrderId(UUID workOrderOperationId,
                                                                                  UUID workOrderId);

    /**
     * Capacity Board rows (C2-8). Day attribution uses the plant's own timezone —
     * {@code function('timezone', zone, instant)} is Postgres's two-argument {@code timezone()},
     * equivalent to {@code instant AT TIME ZONE zone}, called through JPQL's escape hatch rather than
     * a native query so this stays in the same idiom as every other repository query in the repo
     * (no native query precedent exists yet). Operations with no {@code workCenter} (legacy routing
     * rows predating C2-6) or no schedule yet (work order never released) are excluded — they cannot
     * be placed on the board at all, not filtered out by mistake.
     *
     * <p>{@code @EntityGraph} on to-one associations only (workOrder/plant/workCenter/workCalendar)
     * — safe with pagination per rule C13, which forbids join-fetching a <em>collection</em>
     * alongside pagination, not to-one associations.
     */
    @EntityGraph(attributePaths = {"workOrder", "workOrder.plant", "workCenter", "workCenter.workCalendar"})
    @Query("""
            select o
            from WorkOrderOperation o
            where o.workOrder.plant.plantId = :plantId
              and o.workCenter is not null
              and o.plannedStartAt is not null
              and cast(function('timezone', o.workOrder.plant.timezone, o.plannedStartAt) as date)
                  between :from and :to
              and (:workCenterId is null or o.workCenter.workCenterId = :workCenterId)
              and (:status is null or o.workOrder.status = :status)
            """)
    Page<WorkOrderOperation> searchCapacityBoard(@Param("plantId") UUID plantId,
                                                  @Param("workCenterId") UUID workCenterId,
                                                  @Param("status") WorkOrderStatus status,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to,
                                                  Pageable pageable);

    /**
     * One aggregate query (rule C14) for the load side of the Capacity Board / schedule-adjustment
     * overload check — grouped by work center + the calendar day (plant timezone) the operation
     * starts on. Always scoped to {@code loadStatuses} regardless of the board's own {@code status}
     * row filter, so utilization numbers stay consistent across differently-filtered board views
     * (see {@code CapacityBoardService} javadoc).
     */
    @Query("""
            select o.workCenter.workCenterId as workCenterId,
                   cast(function('timezone', o.workOrder.plant.timezone, o.plannedStartAt) as date) as day,
                   sum(o.setupMinutes + o.runMinutesPerUnit * o.workOrder.plannedQuantity) as loadMinutes
            from WorkOrderOperation o
            where o.workOrder.plant.plantId = :plantId
              and o.workCenter is not null
              and o.plannedStartAt is not null
              and o.workOrder.status in :loadStatuses
              and cast(function('timezone', o.workOrder.plant.timezone, o.plannedStartAt) as date)
                  between :from and :to
            group by o.workCenter.workCenterId,
                     cast(function('timezone', o.workOrder.plant.timezone, o.plannedStartAt) as date)
            """)
    List<CapacityLoadProjection> aggregateExistingLoad(@Param("plantId") UUID plantId,
                                                        @Param("from") LocalDate from,
                                                        @Param("to") LocalDate to,
                                                        @Param("loadStatuses") List<WorkOrderStatus> loadStatuses);
}
