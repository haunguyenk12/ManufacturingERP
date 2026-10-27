package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.Baseline;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G11b: everything the cost of one work order is calculated from, read in one transaction by
 * {@link ProductionCostSourceReader}. Lists are already in their stable order (FE Q20): returns by posting time then
 * id, executions by start then id, receipts by approval time then id, decisions by decision time then id, deliveries
 * by posting time then delivery id then line number. Ids break ties as strings, never as UUID numbers (§0.57 #2).
 *
 * @param baseline              {@code null} for a work order released before G11
 * @param foreignPoolMovements  movements of the output pool since release that are not this work order's receipt,
 *                              QC split or a delivery (D3)
 */
public record CostInputs(UUID workOrderId, WorkOrderStatus status, boolean operationTracking,
                         BigDecimal plannedQuantity, BigDecimal finalGood, BigDecimal scrap, BigDecimal rework,
                         Baseline baseline, List<Requirement> requirements, List<IssueLine> issueLines,
                         List<Operation> operations, List<Execution> executions, List<Receipt> receipts,
                         boolean openReceipts, boolean pendingIssues, BigDecimal activeReservedQuantity,
                         List<DeliveryLine> deliveries, int foreignPoolMovements) {

    public record Requirement(UUID componentLineId, BigDecimal requiredQuantity) {
    }

    /** @param consumed {@code null} when consumption is not tracked (work orders before G8c) */
    public record IssueLine(UUID issueId, String issueCode, UUID issueLineId, UUID movementId, UUID componentLineId,
                            UUID itemId, String itemCode, UUID warehouseId, UUID lotId, String uom,
                            BigDecimal quantity, BigDecimal consumed, List<Return> returns) {
    }

    public record Return(UUID returnId, String code, UUID returnLineId, UUID movementId, BigDecimal quantity,
                         Instant postedAt) {
    }

    public record Operation(UUID operationId, Integer sequence, String name, boolean completed, boolean last,
                            BigDecimal input, BigDecimal freshProcessed, BigDecimal pendingRework,
                            BigDecimal transferable, BigDecimal good) {
    }

    /** @param operationId {@code null} for an execution reported without operation tracking */
    public record Execution(UUID executionId, String code, UUID operationId, UUID sourceExecutionId, Instant startedAt,
                            Instant endedAt) {
    }

    /** An {@code APPROVED} receipt (one line), with its QC decisions. */
    public record Receipt(UUID receiptId, String code, UUID warehouseId, Instant approvedAt, BigDecimal quantity,
                          UUID lotId, List<Decision> decisions) {
    }

    public record Decision(UUID decisionId, Instant decidedAt, BigDecimal accepted, BigDecimal rejected,
                           UUID acceptedLotId, UUID rejectedLotId) {
    }

    /** A posted delivery line of the output item from the output warehouse. */
    public record DeliveryLine(UUID deliveryId, String code, UUID lineId, UUID lotId, BigDecimal quantity,
                               UUID movementId) {
    }
}
