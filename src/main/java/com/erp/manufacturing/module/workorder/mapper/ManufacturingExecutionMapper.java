package com.erp.manufacturing.module.workorder.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.core.*;
import com.erp.manufacturing.module.workorder.dto.execution.*;
import com.erp.manufacturing.module.workorder.dto.variance.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ManufacturingExecutionMapper {

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

    public MaterialIssueResponse toResponse(MaterialIssue issue, List<MaterialIssueLine> lines) {
        return new MaterialIssueResponse(
                issue.getIssueId(),
                issue.getWorkOrder().getWorkOrderId(),
                issue.getStatus().name(),
                issue.getIdempotencyKey(),
                issue.getPostedAt(),
                issue.getNote(),
                lines.stream()
                        .sorted(Comparator.comparing(line -> line.getComponentLine().getLineNo()))
                        .map(this::toResponse)
                        .toList());
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue) {
        return toResponse(issue, issue.getLines());
    }

    public MaterialIssueResponse toResponse(MaterialIssue issue, Map<UUID, List<MaterialIssueLine>> linesByIssue) {
        return toResponse(issue, linesByIssue.getOrDefault(issue.getIssueId(), List.of()));
    }

    public MaterialIssueLineResponse toResponse(MaterialIssueLine line) {
        InventoryLot lot = line.getLot();
        return new MaterialIssueLineResponse(
                line.getIssueLineId(),
                line.getComponentLine().getComponentLineId(),
                line.getReservation() != null ? line.getReservation().getReservationId() : null,
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getWarehouse().getWarehouseId(),
                line.getWarehouse().getCode(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                line.getQuantity(),
                line.getStockMovement().getMovementId());
    }

    public WipTransactionResponse toResponse(WipTransaction transaction) {
        return new WipTransactionResponse(
                transaction.getWipTransactionId(),
                transaction.getWorkOrder().getWorkOrderId(),
                transaction.getTransactionType().name(),
                transaction.getStageCode(),
                transaction.getQuantity(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getOccurredAt(),
                transaction.getNote());
    }

    public ProductionReceiptResponse toResponse(ProductionReceipt receipt, List<ProductionReceiptLine> lines) {
        return new ProductionReceiptResponse(
                receipt.getReceiptId(),
                receipt.getWorkOrder().getWorkOrderId(),
                receipt.getStatus().name(),
                receipt.getIdempotencyKey(),
                receipt.getPostedAt(),
                receipt.getNote(),
                lines.stream()
                        .map(this::toResponse)
                        .toList());
    }

    public ProductionReceiptResponse toResponse(ProductionReceipt receipt) {
        return toResponse(receipt, receipt.getLines());
    }

    public ProductionReceiptResponse toResponse(ProductionReceipt receipt,
                                                Map<UUID, List<ProductionReceiptLine>> linesByReceipt) {
        return toResponse(receipt, linesByReceipt.getOrDefault(receipt.getReceiptId(), List.of()));
    }

    public ProductionReceiptLineResponse toResponse(ProductionReceiptLine line) {
        InventoryLot lot = line.getLot();
        return new ProductionReceiptLineResponse(
                line.getReceiptLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getWarehouse().getWarehouseId(),
                line.getWarehouse().getCode(),
                lot != null ? lot.getLotId() : null,
                lot != null ? lot.getLotCode() : null,
                line.getQuantity(),
                line.getStockMovement().getMovementId());
    }
}
