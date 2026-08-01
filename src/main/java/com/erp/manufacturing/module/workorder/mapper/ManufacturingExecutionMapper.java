package com.erp.manufacturing.module.workorder.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.domain.TrackingMethod;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ManufacturingExecutionMapper {

    /**
     * The only status a production execution can be in (spec §5.2). Held here rather than on the
     * entity because there is no transition out of it — see
     * {@link ProductionExecutionResponse#status()}.
     */
    private static final String POSTED_STATUS = "POSTED";

    public MaterialReservationResponse toResponse(MaterialReservation reservation) {
        InventoryLot lot = reservation.getLot();
        return new MaterialReservationResponse(
                reservation.getReservationId(),
                reservation.getWorkOrder().getWorkOrderId(),
                reservation.getComponentLine().getComponentLineId(),
                reservation.getItem().getItemId(),
                reservation.getItem().getCode(),
                reservation.getWarehouse().getWarehouseId(),
                reservation.getWarehouse().getCode(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                reservation.getQuantity(),
                reservation.getConsumedQuantity(),
                reservation.remainingQuantity(),
                reservation.getStatus().name(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    /**
     * {@code usernames} is resolved by the caller in one batch query per page (rule C14), the same
     * contract {@link #toResponse(ProductionReceipt, List, Map)} uses; ids missing from the map
     * render as null.
     */
    public MaterialIssueResponse toResponse(MaterialIssue issue,
                                            List<MaterialIssueLine> lines,
                                            Map<UUID, String> usernames) {
        return new MaterialIssueResponse(
                issue.getIssueId(),
                issue.getCode(),
                issue.getWorkOrder().getWorkOrderId(),
                issue.getWorkOrder().getWorkOrderNo(),
                issue.getStatus().name(),
                issue.getIdempotencyKey(),
                issue.getTraceId(),
                issue.getPostedAt(),
                usernames.get(issue.getCreatedBy()),
                issue.getNote(),
                lines.stream()
                        .sorted(Comparator.comparing(line -> line.getComponentLine().getLineNo()))
                        .map(this::toResponse)
                        .toList());
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue, Map<UUID, String> usernames) {
        return toResponse(issue, issue.getLines(), usernames);
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue,
                                            Map<UUID, List<MaterialIssueLine>> linesByIssue,
                                            Map<UUID, String> usernames) {
        return toResponse(issue, linesByIssue.getOrDefault(issue.getIssueId(), List.of()), usernames);
    }

    public MaterialIssueLineResponse toResponse(MaterialIssueLine line) {
        InventoryLot lot = line.getLot();
        return new MaterialIssueLineResponse(
                line.getIssueLineId(),
                line.getComponentLine().getComponentLineId(),
                line.getReservation() != null ? line.getReservation().getReservationId() : null,
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                line.getWarehouse().getWarehouseId(),
                line.getWarehouse().getCode(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                line.getQuantity(),
                line.getStockMovement().getMovementId(),
                line.isOverIssue(),
                line.getOverrideReason());
    }

    /**
     * {@code usernames} is resolved by the caller in one batch query per page (rule C14) — same
     * contract as the receipt and issue mappers.
     *
     * <p>{@code remainingGood} and {@code completionPercent} are taken from the entity rather than
     * recomputed here, so the shop-floor ceiling keeps exactly one definition (the same reason
     * {@link #toCandidateResponse} defers to {@link WorkOrder#remainingPlannedQuantity()}).
     */
    public ProductionExecutionResponse toResponse(ProductionExecution execution,
                                                  Map<UUID, String> usernames) {
        WorkOrderOperation operation = execution.getOperation();
        WorkOrder workOrder = execution.getWorkOrder();
        return new ProductionExecutionResponse(
                execution.getProductionExecutionId(),
                execution.getCode(),
                POSTED_STATUS,
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                operation == null ? null : operation.getWorkOrderOperationId(),
                operation == null ? null : operation.getSequence(),
                operation == null ? null : operation.getName(),
                operation == null ? null : operation.getWorkCenterCode(),
                execution.getGoodQuantity(),
                execution.getScrapQuantity(),
                execution.getReworkQuantity(),
                execution.getActualStartedAt(),
                execution.getActualEndedAt(),
                execution.getOperatorUserId(),
                usernames.get(execution.getOperatorUserId()),
                execution.getTraceId(),
                execution.getNotes(),
                workOrder.getProductItem().getUnit(),
                workOrder.getActualGoodQuantity(),
                workOrder.getActualScrapQuantity(),
                workOrder.getActualReworkQuantity(),
                workOrder.availableToReceipt(),
                workOrder.remainingPlannedQuantity(),
                workOrder.completionPercent(),
                workOrder.getStatus().name(),
                execution.getCreatedAt());
    }

    /**
     * Candidate row for the shop-floor screen (spec §5.1). {@code remainingQuantity} is derived here
     * rather than stored — it is the same subtraction {@link WorkOrder#remainingPlannedQuantity()}
     * already owns, so the ceiling has exactly one definition.
     */
    public ProductionExecutionCandidateResponse toCandidateResponse(WorkOrder workOrder) {
        return new ProductionExecutionCandidateResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getCode(),
                workOrder.getProductItem().getName(),
                workOrder.getProductItem().getUnit(),
                workOrder.getPlannedQuantity(),
                workOrder.getActualGoodQuantity(),
                workOrder.remainingPlannedQuantity(),
                workOrder.getPlannedEndAt());
    }

    /**
     * Candidate row for the receipt screen (spec §6.4).
     *
     * <p>{@code openReceiptQuantity} — how much this work order's still-open receipts have already
     * claimed — has to be passed in: {@link WorkOrder#availableToReceipt()} does not deduct it (B16
     * leaves that to the caller), so the entity alone cannot produce the number the query filtered
     * on. The caller resolves it for the whole page in one batch (rule C14).
     */
    public ProductionReceiptCandidateResponse toReceiptCandidateResponse(WorkOrder workOrder,
                                                                         BigDecimal openReceiptQuantity) {
        Item product = workOrder.getProductItem();
        return new ProductionReceiptCandidateResponse(
                workOrder.getWorkOrderId(),
                workOrder.getWorkOrderNo(),
                workOrder.getStatus().name(),
                product.getItemId(),
                product.getCode(),
                product.getName(),
                product.getUnit(),
                workOrder.getActualGoodQuantity(),
                workOrder.getCompletedQuantity(),
                workOrder.availableToReceipt().subtract(openReceiptQuantity),
                TrackingMethod.of(product.isLotTracked()).name(),
                workOrder.getPlannedEndAt());
    }

    public WipTransactionResponse toResponse(WipTransaction transaction) {
        return new WipTransactionResponse(
                transaction.getWipTransactionId(),
                transaction.getWorkOrder().getWorkOrderId(),
                transaction.getTransactionType().name(),
                transaction.getOperation() == null ? null : transaction.getOperation().getWorkOrderOperationId(),
                transaction.getStageCode(),
                transaction.getQuantity(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getOccurredAt(),
                transaction.getNote());
    }

    /**
     * Flat single-line shape (spec §6.3). {@code usernames} is resolved by the caller in one batch
     * query so mapping a page of receipts does not become N+1 (rule C14); ids missing from the map
     * simply render as null.
     */
    public ProductionReceiptResponse toResponse(ProductionReceipt receipt,
                                                List<ProductionReceiptLine> lines,
                                                Map<UUID, String> usernames) {
        ProductionReceiptLine line = lines.isEmpty() ? null : lines.get(0);
        InventoryLot lot = line == null ? null : line.getLot();
        StockMovement movement = line == null ? null : line.getStockMovement();
        return new ProductionReceiptResponse(
                receipt.getReceiptId(),
                receipt.getCode(),
                receipt.getWorkOrder().getWorkOrderId(),
                receipt.getWorkOrder().getWorkOrderNo(),
                receipt.getStatus().name(),
                receipt.getIdempotencyKey(),
                line == null ? null : TrackingMethod.of(line.getItem().isLotTracked()).name(),
                line == null ? null : line.getItem().getItemId(),
                line == null ? null : line.getItem().getCode(),
                line == null ? null : line.getItem().getName(),
                line == null ? null : line.getItem().getUnit(),
                line == null ? null : line.getWarehouse().getWarehouseId(),
                line == null ? null : line.getWarehouse().getCode(),
                lot == null ? null : lot.getLotId(),
                lot != null ? lot.getLotCode() : (line == null ? null : line.getRequestedLotCode()),
                // Null for output that is not lot-tracked: there is no lot to carry HOLD, so the QC
                // verdict lives on qcResult instead (D5, B40). Absent status ≠ "not decided".
                lot == null ? null : lot.getStatus().name(),
                line == null ? null : line.getQuantity(),
                movement == null ? null : movement.getMovementId(),
                receipt.getPostedAt(),
                receipt.getNote(),
                receipt.getSubmittedAt(),
                receipt.getApprovedAt(),
                receipt.getRejectedAt(),
                receipt.getRejectReason(),
                receipt.getQcResult() != null ? receipt.getQcResult().name() : null,
                receipt.getQcReason(),
                receipt.getQcAt(),
                usernames.get(receipt.getCreatedBy()),
                usernames.get(receipt.getApprovedBy()),
                usernames.get(receipt.getQcBy()),
                receipt.getTraceId(),
                splitTraceIds(receipt.getSourceWipTraceIds()));
    }

    public ProductionReceiptResponse toResponse(ProductionReceipt receipt, Map<UUID, String> usernames) {
        return toResponse(receipt, receipt.getLines(), usernames);
    }

    public ProductionReceiptResponse toResponse(ProductionReceipt receipt,
                                                Map<UUID, List<ProductionReceiptLine>> linesByReceipt,
                                                Map<UUID, String> usernames) {
        return toResponse(receipt, linesByReceipt.getOrDefault(receipt.getReceiptId(), List.of()), usernames);
    }

    private List<String> splitTraceIds(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }
}
