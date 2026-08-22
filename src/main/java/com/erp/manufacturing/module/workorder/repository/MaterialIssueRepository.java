package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, UUID> {

    Optional<MaterialIssue> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.componentLine", "lines.reservation",
            "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement"})
    Optional<MaterialIssue> findWithLinesByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.componentLine", "lines.reservation",
            "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement", "lines.sourceExecution"})
    Optional<MaterialIssue> findWithLinesByIssueId(UUID issueId);

    /**
     * One work order's issue history, optionally narrowed to a status.
     *
     * <p>{@code status} exists so the Over-BOM approval queue is a server-side filter, not a client
     * that pages through POSTED history hoping a PENDING_APPROVAL row appears. Filtering in the
     * browser breaks the moment a work order has more issues than one page: the queue would look
     * empty while requests wait.
     */
    @Query("""
            select i
            from MaterialIssue i
            where i.workOrder.workOrderId = :workOrderId
              and (:status is null or i.status = :status)
            """)
    Page<MaterialIssue> findByWorkOrder(@Param("workOrderId") UUID workOrderId,
                                        @Param("status") MaterialIssueStatus status,
                                        Pageable pageable);

    /**
     * Flat plant-scoped history (spec §4.1). {@code workOrderId} is an optional narrowing filter.
     *
     * <p>{@code join fetch w} is required, not an optimisation — see the sibling query on
     * {@code ProductionReceiptRepository.findByPlant} for why. The {@code lines} collection is
     * deliberately not fetched here (rule C13); the service batches it via
     * {@code findByIssueIssueIdIn}.
     */
    @Query(value = """
            select i
            from MaterialIssue i
            join fetch i.workOrder w
            where w.plant.plantId = :plantId
              and (:workOrderId is null or w.workOrderId = :workOrderId)
              and (:status is null or i.status = :status)
            """,
            countQuery = """
            select count(i)
            from MaterialIssue i
            where i.workOrder.plant.plantId = :plantId
              and (:workOrderId is null or i.workOrder.workOrderId = :workOrderId)
              and (:status is null or i.status = :status)
            """)
    Page<MaterialIssue> findByPlant(@Param("plantId") UUID plantId,
                                    @Param("workOrderId") UUID workOrderId,
                                    @Param("status") MaterialIssueStatus status,
                                    Pageable pageable);
}
