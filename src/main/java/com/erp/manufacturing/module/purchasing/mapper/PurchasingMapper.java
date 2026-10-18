package com.erp.manufacturing.module.purchasing.mapper;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.Map;

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
                supplier.getUpdatedAt(),
                supplier.getContactName(),
                supplier.getNotes());
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

    /**
     * G7a: purchase order lines in a stable order. Lines saved in one flush get the same
     * {@code created_at} once Postgres rounds it to microseconds, so the id breaks the tie — without
     * it two reads of the same order listed the lines differently (found by the G7a smoke test), and
     * a field error such as {@code lines[1].unitPrice} could name the wrong line.
     */
    public static final Comparator<PurchaseOrderLine> LINE_ORDER = Comparator
            .comparing(PurchaseOrderLine::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            // As text, not UUID.compareTo: that compares signed longs, so "ffff…" would sort first.
            .thenComparing(line -> String.valueOf(line.getPurchaseOrderLineId()));

    public PurchaseOrderResponse toResponse(PurchaseOrder order, boolean includeLines) {
        return toResponse(order, includeLines, Map.of(), false, List.of());
    }

    /**
     * G7a: the full projection. {@code sources} by purchase order line id (detail only),
     * {@code sourceLocked} and {@code history} are resolved by the service in batch queries.
     */
    public PurchaseOrderResponse toResponse(PurchaseOrder order,
                                            boolean includeLines,
                                            Map<UUID, List<PurchaseOrderLineSourceResponse>> sources,
                                            boolean sourceLocked,
                                            List<PurchaseOrderHistoryResponse> history) {
        List<PurchaseOrderLineResponse> lines = includeLines
                ? order.getLines().stream()
                .sorted(LINE_ORDER)
                .map(line -> toResponse(line, sources.getOrDefault(line.getPurchaseOrderLineId(), List.of())))
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
                lines,
                order.getPurchaseOrderNo(),
                order.getVersion(),
                order.getWarehouse().getName(),
                order.getReference(),
                includeLines ? singleCurrency(order) : null,
                sourceLocked,
                order.getStatusReason(),
                order.getConfirmedAt(),
                order.getCancelledAt(),
                order.getClosedAt(),
                order.getCreatedBy(),
                history);
    }

    public PurchaseOrderLineResponse toResponse(PurchaseOrderLine line) {
        return toResponse(line, List.of());
    }

    public PurchaseOrderLineResponse toResponse(PurchaseOrderLine line, List<PurchaseOrderLineSourceResponse> sources) {
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
                line.getExpectedDate(),
                line.getItem().getUnit(),
                line.getClosedQuantity(),
                line.openQuantity(),
                sources);
    }

    private static String singleCurrency(PurchaseOrder order) {
        List<String> currencies = order.getLines().stream()
                .map(PurchaseOrderLine::getCurrencyCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return currencies.size() == 1 ? currencies.get(0) : null;
    }

    /** G7b: receipt lines in a stable order — same rule as {@link #LINE_ORDER}. */
    public static final Comparator<GoodsReceiptLine> RECEIPT_LINE_ORDER = Comparator
            .comparing(GoodsReceiptLine::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(line -> String.valueOf(line.getGoodsReceiptLineId()));

    public GoodsReceiptResponse toResponse(GoodsReceipt receipt, boolean includeLines) {
        List<GoodsReceiptLineResponse> lines = includeLines
                ? receipt.getLines().stream()
                .sorted(RECEIPT_LINE_ORDER)
                .map(this::toResponse)
                .toList()
                : List.of();
        PurchaseOrder order = receipt.getPurchaseOrder();
        Supplier supplier = order.getSupplier();
        return new GoodsReceiptResponse(
                receipt.getGoodsReceiptId(),
                order.getPurchaseOrderId(),
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
                lines,
                receipt.getReceiptNo(),
                receipt.getVersion(),
                receipt.getReceivedDate(),
                receipt.getReference(),
                order.getCompany().getCompanyId(),
                order.getPlant().getPlantId(),
                order.getPurchaseOrderNo(),
                supplier.getSupplierId(),
                supplier.getCode(),
                supplier.getName(),
                receipt.getWarehouse().getName(),
                receipt.getPostedBy(),
                receipt.getCancelledBy(),
                order.getStatus().name(),
                order.getVersion());
    }

    public GoodsReceiptLineResponse toResponse(GoodsReceiptLine line) {
        InventoryLot lot = line.getLot();
        PurchaseOrderLine orderLine = line.getPurchaseOrderLine();
        UUID movementId = line.getStockMovement() == null ? null : line.getStockMovement().getMovementId();
        return new GoodsReceiptLineResponse(
                line.getGoodsReceiptLineId(),
                orderLine.getPurchaseOrderLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                lot == null ? null : lot.getLotId(),
                line.getLotCode(),
                line.getReceivedQuantity(),
                movementId,
                trackingPolicy(line.getItem()),
                line.getItem().getUnit(),
                orderLine.getUnitPrice(),
                orderLine.getCurrencyCode(),
                line.getExpiresOn(),
                lot == null ? null : lot.getStatus().name(),
                movementId == null ? List.of() : List.of(movementId));
    }

    /** G7b: {@code LOT_TRACKED} / {@code SERIAL_TRACKED} / {@code NON_TRACKED} (FE trackingPolicy). */
    public static String trackingPolicy(Item item) {
        if (item.isLotTracked()) {
            return "LOT_TRACKED";
        }
        return item.isSerialTracked() ? "SERIAL_TRACKED" : "NON_TRACKED";
    }

    public GoodsReceiptCandidateResponse toCandidate(PurchaseOrder order, List<PurchaseOrderLine> openLines) {
        return new GoodsReceiptCandidateResponse(
                order.getPurchaseOrderId(),
                order.getPurchaseOrderNo(),
                order.getVersion(),
                order.getStatus().name(),
                order.getSupplier().getSupplierId(),
                order.getSupplier().getCode(),
                order.getSupplier().getName(),
                order.getWarehouse().getWarehouseId(),
                order.getWarehouse().getCode(),
                order.getWarehouse().getName(),
                order.getOrderDate(),
                order.getReference(),
                openLines.stream().sorted(LINE_ORDER).map(line -> new GoodsReceiptCandidateLineResponse(
                        line.getPurchaseOrderLineId(),
                        line.getItem().getItemId(),
                        line.getItem().getCode(),
                        line.getItem().getName(),
                        line.getItem().getUnit(),
                        trackingPolicy(line.getItem()),
                        line.getOrderedQuantity(),
                        line.getReceivedQuantity(),
                        line.getClosedQuantity(),
                        line.openQuantity(),
                        line.getExpectedDate(),
                        line.getUnitPrice(),
                        line.getCurrencyCode())).toList());
    }
}
