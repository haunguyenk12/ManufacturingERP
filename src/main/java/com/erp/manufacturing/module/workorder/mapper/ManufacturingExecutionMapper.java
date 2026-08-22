package com.erp.manufacturing.module.workorder.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.SerialNumber;
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
     * {@code usernames} and {@code lotCodes} are resolved by the caller in one batch query each per
     * page (rule C14), the same contract {@link #toResponse(ProductionReceipt, List, Map)} uses; ids
     * missing from either map render as null.
     */
    public MaterialIssueResponse toResponse(MaterialIssue issue,
                                            List<MaterialIssueLine> lines,
                                            Map<UUID, String> usernames,
                                            Map<UUID, String> lotCodes) {
        return new MaterialIssueResponse(
                issue.getIssueId(),
                issue.getCode(),
                issue.getWorkOrder().getWorkOrderId(),
                issue.getWorkOrder().getWorkOrderNo(),
                issue.getStatus().name(),
                issue.getIdempotencyKey(),
                issue.getTraceId(),
                issue.getPostedAt(),
                usernameOf(usernames, issue.getCreatedBy()),
                issue.getNote(),
                lines.stream()
                        .sorted(Comparator.comparing(line -> line.getComponentLine().getLineNo()))
                        .map(line -> toResponse(line, lotCodes))
                        .toList(),
                issue.getRequestedAt(),
                issue.getDecidedAt(),
                issue.getDecidedBy(),
                issue.getRejectionReason());
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue,
                                            Map<UUID, String> usernames,
                                            Map<UUID, String> lotCodes) {
        return toResponse(issue, issue.getLines(), usernames, lotCodes);
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue,
                                            Map<UUID, List<MaterialIssueLine>> linesByIssue,
                                            Map<UUID, String> usernames,
                                            Map<UUID, String> lotCodes) {
        return toResponse(issue, linesByIssue.getOrDefault(issue.getIssueId(), List.of()),
                usernames, lotCodes);
    }

    /**
     * @param lotCodes lot id → lot code, batch-resolved by the caller for lines that named a lot but
     *        are not linked to one yet. A PENDING_APPROVAL line has no {@code lot} — that link is
     *        only made when approval posts the movement — so without this map the approval queue
     *        shows the manager a raw UUID and the client has to fetch each lot separately.
     */
    /**
     * The code the requester identified their lot by. A typed code is echoed back exactly as given;
     * when they picked a lot by id instead, the code is filled in from the batch map. Echo wins so a
     * request that named something the resolver disagrees with still shows what was actually asked
     * for, rather than a code the operator never typed.
     */
    /**
     * Null-safe read of the batch username map.
     *
     * <p>The guard is not defensive padding: a document may legitimately carry no actor — a DRAFT
     * receipt has no {@code approvedBy}, and rows written outside a user request have no
     * {@code createdBy}. When <em>every</em> id on a page is null the batch lookup is handed an empty
     * set and returns {@code Map.of()}, whose {@code get(null)} throws NullPointerException rather
     * than returning null. That turns a perfectly ordinary page into a 500. Same trap, same fix as
     * the recent movements feed in CLAUDE.md §0.43.
     */
    private String usernameOf(Map<UUID, String> usernames, UUID userId) {
        return userId == null ? null : usernames.get(userId);
    }

    /**
     * The code of the lot this line will actually consume, for a line not yet linked to one.
     *
     * <p>Precedence follows {@code InventoryMovementService.resolveExistingLot}, which takes the id
     * whenever one is present and only falls back to the typed code. A request carrying both a
     * {@code requestedLotId} and a disagreeing {@code requestedLotCode} would otherwise show the
     * approver one code while approval consumes a different lot — the one thing an approval screen
     * must never do. The typed code remains the fallback, including when the id resolves to nothing,
     * so a line never renders blank while it still has something to say.
     */
    private String requestedLotCode(MaterialIssueLine line, Map<UUID, String> lotCodes) {
        if (line.getRequestedLotId() != null) {
            String resolved = lotCodes.get(line.getRequestedLotId());
            if (resolved != null) {
                return resolved;
            }
        }
        return line.getRequestedLotCode();
    }

    public MaterialIssueLineResponse toResponse(MaterialIssueLine line, Map<UUID, String> lotCodes) {
        InventoryLot lot = line.getLot();
        SerialNumber serial = line.getSerial();
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
                lot != null ? lot.getLotId() : line.getRequestedLotId(),
                lot != null ? lot.getLotCode() : requestedLotCode(line, lotCodes),
                serial != null ? serial.getSerialId() : line.getRequestedSerialId(),
                serial != null ? serial.getSerialCode() : null,
                line.getQuantity(),
                line.getStockMovement() == null ? null : line.getStockMovement().getMovementId(),
                line.isOverIssue(),
                line.getOverrideReason(),
                line.getReasonCode() == null ? null : line.getReasonCode().name(),
                line.getSourceExecution() == null ? null : line.getSourceExecution().getProductionExecutionId());
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
                usernameOf(usernames, execution.getOperatorUserId()),
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
                TrackingMethod.of(product.isLotTracked(), product.isSerialTracked()).name(),
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
        SerialNumber serial = line == null ? null : line.getSerial();
        StockMovement movement = line == null ? null : line.getStockMovement();
        return new ProductionReceiptResponse(
                receipt.getReceiptId(),
                receipt.getCode(),
                receipt.getWorkOrder().getWorkOrderId(),
                receipt.getWorkOrder().getWorkOrderNo(),
                receipt.getStatus().name(),
                receipt.getIdempotencyKey(),
                line == null ? null : TrackingMethod.of(line.getItem().isLotTracked(), line.getItem().isSerialTracked()).name(),
                line == null ? null : line.getItem().getItemId(),
                line == null ? null : line.getItem().getCode(),
                line == null ? null : line.getItem().getName(),
                line == null ? null : line.getItem().getUnit(),
                line == null ? null : line.getWarehouse().getWarehouseId(),
                line == null ? null : line.getWarehouse().getCode(),
                lot == null ? null : lot.getLotId(),
                lot != null ? lot.getLotCode() : (line == null ? null : line.getRequestedLotCode()),
                serial == null ? null : serial.getSerialId(),
                serial != null ? serial.getSerialCode() : (line == null ? null : line.getRequestedSerialCode()),
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
                usernameOf(usernames, receipt.getCreatedBy()),
                usernameOf(usernames, receipt.getApprovedBy()),
                usernameOf(usernames, receipt.getQcBy()),
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
