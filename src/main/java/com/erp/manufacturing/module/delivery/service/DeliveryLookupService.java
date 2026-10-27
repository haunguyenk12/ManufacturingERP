package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.domain.DeliveryStatus;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G10b: entry point for {@code module/sales} and {@code module/billing} into deliveries (rule C7). Repository-only, no {@code @PreAuthorize} —
 * callers are guarded on the order's plant. Delivered quantity is always summed from POSTED lines, never stored.
 */
@Service
@RequiredArgsConstructor
public class DeliveryLookupService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLineRepository deliveryLineRepository;

    /** D per sales order line (absent = nothing delivered), one query (C14). */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> sumDeliveredBySalesOrderLineIds(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> delivered = new HashMap<>();
        for (DeliveryLineRepository.LineQuantityProjection row
                : deliveryLineRepository.sumPostedBySalesOrderLineIds(salesOrderLineIds)) {
            delivered.put(row.getSalesOrderLineId(), row.getQuantity());
        }
        return delivered;
    }

    /** G10c: delivery lines by id with their delivery (status, order, date, code), one query. */
    @Transactional(readOnly = true)
    public List<DeliveryLine> findLines(Collection<UUID> deliveryLineIds) {
        if (deliveryLineIds == null || deliveryLineIds.isEmpty()) {
            return List.of();
        }
        return deliveryLineRepository.findWithDeliveryByDeliveryLineIdIn(deliveryLineIds);
    }

    /** G10c: the lines of the order's POSTED deliveries, oldest document first. */
    @Transactional(readOnly = true)
    public List<DeliveryLine> findPostedLines(UUID salesOrderId) {
        return deliveryLineRepository.findPostedBySalesOrderId(salesOrderId);
    }

    /** G10d: Cd per sales order line — what DRAFT deliveries claim (absent = nothing), one query. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> sumDraftBySalesOrderLineIds(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds == null || salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> drafts = new HashMap<>();
        for (DeliveryLineRepository.LineQuantityProjection row
                : deliveryLineRepository.sumDraftBySalesOrderLineIds(salesOrderLineIds, null)) {
            drafts.put(row.getSalesOrderLineId(), row.getQuantity());
        }
        return drafts;
    }

    /** FE R26/Q37: a DRAFT or POSTED delivery stands behind the order, so the order cannot be cancelled. */
    @Transactional(readOnly = true)
    public boolean hasOpenDeliveries(UUID salesOrderId) {
        return deliveryRepository.existsBySalesOrderIdAndStatusIn(
                salesOrderId, List.of(DeliveryStatus.DRAFT, DeliveryStatus.POSTED));
    }

    /** G11b: the posted delivery lines of one output pool (item × warehouse), oldest first — production costing. */
    @Transactional(readOnly = true)
    public List<DeliveryLine> findPostedPoolLines(UUID itemId, UUID warehouseId) {
        return deliveryLineRepository.findPostedByItemAndWarehouse(itemId, warehouseId);
    }

    /** G11c: one delivery with its lines, or empty. */
    @Transactional(readOnly = true)
    public java.util.Optional<com.erp.manufacturing.module.delivery.domain.Delivery> findDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId);
    }

    /** G11c: the lines of one delivery in line order. */
    @Transactional(readOnly = true)
    public List<DeliveryLine> findLinesOfDelivery(UUID deliveryId) {
        return deliveryLineRepository.findByDeliveryDeliveryIdOrderByLineNo(deliveryId);
    }
}
