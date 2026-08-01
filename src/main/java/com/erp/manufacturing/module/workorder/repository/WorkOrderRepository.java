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

    /**
     * Free-text {@code search} (spec §3.2) matches the work order number or the product SKU.
     *
     * <p>The join on {@code productItem} is written out rather than reached through
     * {@code w.productItem.code}: a path expression compiles to an <em>implicit</em> INNER JOIN, which
     * is exactly how debt #8 silently dropped rows from the availability queries. Here the association
     * is {@code optional = false} so an inner join is correct either way — spelling it out is what
     * keeps the next reader from having to work that out again.
     */
    @Query("""
            select w
            from WorkOrder w
            join w.productItem p
            where w.plant.plantId = :plantId
              and (:status is null or w.status = :status)
              and (:productItemId is null or p.itemId = :productItemId)
              and (:search is null
                   or lower(w.workOrderNo) like lower(concat('%', :search, '%'))
                   or lower(p.code) like lower(concat('%', :search, '%')))
            """)
    Page<WorkOrder> search(@Param("plantId") UUID plantId,
                           @Param("status") WorkOrderStatus status,
                           @Param("productItemId") UUID productItemId,
                           @Param("search") String search,
                           Pageable pageable);

    /**
     * Work orders the shop floor may still report production against (spec §5.1, invariant B75).
     *
     * <p>Two conditions, not one. The status filter is the obvious half; the quantity filter is the
     * half that is easy to miss: spec §5.1 forbids cumulative good from exceeding
     * {@code plannedQuantity}, so a work order that already reached its plan cannot accept another
     * report and must not be offered as a candidate.
     *
     * <p>{@code join fetch} on {@code productItem} is safe with pagination because it is a
     * {@code @ManyToOne} — rule C13 only forbids fetching a <em>collection</em> alongside paging.
     * It keeps the whole page to one query (C14) instead of one lookup per row.
     */
    @Query(value = """
            select w
            from WorkOrder w
            join fetch w.productItem
            where w.plant.plantId = :plantId
              and w.status in :statuses
              and w.actualGoodQuantity < w.plannedQuantity
            """,
            countQuery = """
            select count(w)
            from WorkOrder w
            where w.plant.plantId = :plantId
              and w.status in :statuses
              and w.actualGoodQuantity < w.plannedQuantity
            """)
    Page<WorkOrder> findExecutionCandidates(@Param("plantId") UUID plantId,
                                            @Param("statuses") Collection<WorkOrderStatus> statuses,
                                            Pageable pageable);

    /**
     * Work orders that still have finished output waiting to be warehoused (spec §6.2, invariant
     * B76). <b>Three</b> conditions, and this is deliberately <em>not</em> a copy of
     * {@link #findExecutionCandidates}:
     *
     * <ol>
     *   <li><b>Status</b> is {@code canReceipt()} — {@code RELEASED}, {@code IN_PROGRESS} <em>and</em>
     *       {@code COMPLETED}. Dropping {@code COMPLETED} would rebuild debt #25 (D11) in the read
     *       model: {@code reportProduction} completes a work order the moment cumulative good reaches
     *       the plan, so the <em>last</em> receipt of every such work order would vanish from the
     *       screen. This is the opposite of B75, where {@code COMPLETED} is correctly excluded
     *       because no further production may be reported.</li>
     *   <li><b>Quantity</b> is {@code actualGood - completed - (open receipts)}, not just
     *       {@code actualGood - completed}. Receipts in {@code DRAFT} or {@code PENDING_APPROVAL}
     *       have already claimed part of the output (B16), so leaving them out would offer the
     *       operator a row that {@code postNew} is certain to refuse with
     *       {@code PLANNED_QUANTITY_EXCEEDED}.</li>
     *   <li><b>Plant</b> scope.</li>
     * </ol>
     *
     * <p>The netting sits in the query rather than in a mapper so the gate has exactly one
     * definition; {@code ProductionReceiptRepository.sumOpenQuantityByWorkOrderIds} then reports the
     * same number for display, in one batch for the whole page.
     *
     * <p>🔴 All three conditions live in JPQL, so a mocked repository cannot test any of them — see
     * {@code WorkOrderRepositoryIT.findReceiptCandidates_*} (rule R7).
     */
    @Query(value = """
            select w
            from WorkOrder w
            join fetch w.productItem
            where w.plant.plantId = :plantId
              and w.status in :statuses
              and w.actualGoodQuantity - w.completedQuantity - (
                    select coalesce(sum(l.quantity), 0)
                    from ProductionReceiptLine l
                    where l.receipt.workOrder.workOrderId = w.workOrderId
                      and l.receipt.status in (
                            com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                            com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL)
              ) > 0
            """,
            countQuery = """
            select count(w)
            from WorkOrder w
            where w.plant.plantId = :plantId
              and w.status in :statuses
              and w.actualGoodQuantity - w.completedQuantity - (
                    select coalesce(sum(l.quantity), 0)
                    from ProductionReceiptLine l
                    where l.receipt.workOrder.workOrderId = w.workOrderId
                      and l.receipt.status in (
                            com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.DRAFT,
                            com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus.PENDING_APPROVAL)
              ) > 0
            """)
    Page<WorkOrder> findReceiptCandidates(@Param("plantId") UUID plantId,
                                          @Param("statuses") Collection<WorkOrderStatus> statuses,
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
