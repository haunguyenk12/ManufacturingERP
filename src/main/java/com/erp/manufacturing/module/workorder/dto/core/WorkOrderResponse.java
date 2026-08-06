package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkOrderResponse(
        UUID workOrderId,
        UUID companyId,
        UUID plantId,
        String plantCode,
        String workOrderNo,
        UUID productItemId,
        String productItemCode,
        String productItemName,
        /** Unit of measure of the output item (spec §3.3 "Sản phẩm"). */
        String outputUom,
        UUID bomId,
        String bomRevision,
        /**
         * When the BOM snapshot was taken (spec §3.3). This is the work order's own
         * {@code createdAt}: the BOM is {@code optional = false} and is captured during creation, so
         * the two instants are the same by construction.
         *
         * <p>Deliberately asymmetric with {@link #routingCapturedAt()}, which <em>is</em> a stored
         * column: a routing is optional, so a work order can exist without one and "when was the
         * routing captured" is a genuinely separate question with a genuinely separate answer
         * (possibly none). Do not add a {@code bom_captured_at} column "for symmetry".
         */
        Instant bomCapturedAt,
        UUID sourceRoutingId,
        String sourceRoutingCode,
        String sourceRoutingVersion,
        Instant routingCapturedAt,
        /**
         * Which MRP run and proposal produced this work order (spec §3.3 "Nguồn gốc", V39). All
         * three are null on manually created work orders and on anything created before F8.
         */
        UUID planningRunId,
        String planningRunCode,
        UUID planningProposalId,
        UUID outputWarehouseId,
        String outputWarehouseCode,
        BigDecimal plannedQuantity,
        /** Quantity already receipted into stock. */
        BigDecimal completedQuantity,
        BigDecimal remainingQuantity,
        /** Cumulative shop-floor output (F5) — what drives completion, unlike {@code completedQuantity}. */
        BigDecimal actualGoodQuantity,
        BigDecimal actualScrapQuantity,
        BigDecimal actualReworkQuantity,
        /** {@code actualGoodQuantity - completedQuantity}: how much a receipt may still claim. */
        BigDecimal availableToReceipt,
        String status,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant releasedAt,
        /**
         * When the shop floor first reported production (spec §5.3, V39). Null when nothing has been
         * reported yet, or when the work order predates F8.
         */
        Instant executionStartedAt,
        /**
         * Spec §5.3 {@code executionCompletedAt} — the same instant as {@link #completedAt()}, not a
         * second stored column. {@code WorkOrder.complete()} has exactly one call site (shop-floor
         * reporting, invariant B53), so completion of the work order <em>is</em> completion of
         * execution.
         */
        Instant executionCompletedAt,
        Instant completedAt,
        Instant cancelledAt,
        /** Why it was cancelled (spec §3.2, F7). Null on work orders cancelled before F7. */
        String cancelReason,
        Instant blockedAt,
        String blockReason,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<WorkOrderComponentLineResponse> componentLines,
        List<WorkOrderOperationResponse> operations,
        /** Sales order lines this output is earmarked for (F6). Empty for unallocated work orders. */
        List<WorkOrderDemandAllocationResponse> allocations,
        /** When this work order was reconciled and locked (P6). Null until {@code CLOSED}. */
        Instant closedAt
) {}
