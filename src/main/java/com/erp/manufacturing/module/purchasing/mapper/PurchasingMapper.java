package com.erp.manufacturing.module.purchasing.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PurchasingMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getSupplierId(),
                supplier.getCompany().getCompanyId(),
                supplier.getCompany().getCode(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getTaxCode(),
                supplier.getStatus().name(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }

    public ItemSupplierResponse toResponse(ItemSupplier itemSupplier) {
        return new ItemSupplierResponse(
                itemSupplier.getItemSupplierId(),
                itemSupplier.getItem().getItemId(),
                itemSupplier.getItem().getCode(),
                itemSupplier.getItem().getName(),
                itemSupplier.getSupplier().getSupplierId(),
                itemSupplier.getSupplier().getCode(),
                itemSupplier.getSupplier().getName(),
                itemSupplier.getSupplierItemCode(),
                itemSupplier.getLeadTimeDays(),
                itemSupplier.getMinimumOrderQuantity(),
                itemSupplier.getUnitPrice(),
                itemSupplier.getCurrencyCode(),
                itemSupplier.isPreferred(),
                itemSupplier.getStatus().name(),
                itemSupplier.getCreatedAt(),
                itemSupplier.getUpdatedAt());
    }

    public PurchaseRequisitionResponse toResponse(PurchaseRequisition requisition, boolean includeLines) {
        List<PurchaseRequisitionLineResponse> lines = includeLines
                ? requisition.getLines().stream()
                .sorted(Comparator.comparing(PurchaseRequisitionLine::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList()
                : List.of();
        return new PurchaseRequisitionResponse(
                requisition.getPurchaseRequisitionId(),
                requisition.getCompany().getCompanyId(),
                requisition.getCompany().getCode(),
                requisition.getPlant().getPlantId(),
                requisition.getPlant().getCode(),
                requisition.getWarehouse().getWarehouseId(),
                requisition.getWarehouse().getCode(),
                requisition.getRequisitionNo(),
                requisition.getStatus().name(),
                requisition.getNeededByDate(),
                requisition.getSourceType(),
                requisition.getSourceId(),
                requisition.getDecisionNote(),
                requisition.getCreatedAt(),
                requisition.getUpdatedAt(),
                lines);
    }

    public PurchaseRequisitionLineResponse toResponse(PurchaseRequisitionLine line) {
        Supplier supplier = line.getSupplier();
        return new PurchaseRequisitionLineResponse(
                line.getPurchaseRequisitionLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                supplier == null ? null : supplier.getSupplierId(),
                supplier == null ? null : supplier.getCode(),
                supplier == null ? null : supplier.getName(),
                line.getRequestedQuantity(),
                line.getApprovedQuantity(),
                line.getNeededByDate(),
                line.getNote());
    }

    public PurchaseOrderResponse toResponse(PurchaseOrder order, boolean includeLines) {
        List<PurchaseOrderLineResponse> lines = includeLines
                ? order.getLines().stream()
                .sorted(Comparator.comparing(PurchaseOrderLine::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList()
                : List.of();
        return new PurchaseOrderResponse(
                order.getPurchaseOrderId(),
                order.getCompany().getCompanyId(),
                order.getCompany().getCode(),
                order.getPlant().getPlantId(),
                order.getPlant().getCode(),
                order.getWarehouse().getWarehouseId(),
                order.getWarehouse().getCode(),
                order.getSupplier().getSupplierId(),
                order.getSupplier().getCode(),
                order.getSupplier().getName(),
                order.getPurchaseOrderNo(),
                order.getStatus().name(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getSourceRequisition() == null ? null : order.getSourceRequisition().getPurchaseRequisitionId(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                lines);
    }

    public PurchaseOrderLineResponse toResponse(PurchaseOrderLine line) {
        return new PurchaseOrderLineResponse(
                line.getPurchaseOrderLineId(),
                line.getPurchaseRequisitionLine() == null
                        ? null
                        : line.getPurchaseRequisitionLine().getPurchaseRequisitionLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.remainingQuantity(),
                line.getUnitPrice(),
                line.getCurrencyCode(),
                line.getExpectedDate());
    }

    public GoodsReceiptResponse toResponse(GoodsReceipt receipt, boolean includeLines) {
        List<GoodsReceiptLineResponse> lines = includeLines
                ? receipt.getLines().stream()
                .sorted(Comparator.comparing(GoodsReceiptLine::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList()
                : List.of();
        return new GoodsReceiptResponse(
                receipt.getGoodsReceiptId(),
                receipt.getPurchaseOrder().getPurchaseOrderId(),
                receipt.getWarehouse().getWarehouseId(),
                receipt.getWarehouse().getCode(),
                receipt.getReceiptNo(),
                receipt.getStatus().name(),
                receipt.getPostedAt(),
                receipt.getIdempotencyKey(),
                receipt.getNote(),
                receipt.getCancelledAt(),
                receipt.getCancelNote(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt(),
                lines);
    }

    public GoodsReceiptLineResponse toResponse(GoodsReceiptLine line) {
        InventoryLot lot = line.getLot();
        return new GoodsReceiptLineResponse(
                line.getGoodsReceiptLineId(),
                line.getPurchaseOrderLine().getPurchaseOrderLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                lot == null ? null : lot.getLotId(),
                line.getLotCode(),
                line.getReceivedQuantity(),
                line.getStockMovement().getMovementId());
    }
}
