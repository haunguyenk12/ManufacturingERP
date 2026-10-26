package com.erp.manufacturing.module.delivery.service;

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
 * G10b: entry point for {@code module/sales} into deliveries (rule C7). Repository-only, no {@code @PreAuthorize} —
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

    /** FE R26/Q37: a DRAFT or POSTED delivery stands behind the order, so the order cannot be cancelled. */
    @Transactional(readOnly = true)
    public boolean hasOpenDeliveries(UUID salesOrderId) {
        return deliveryRepository.existsBySalesOrderIdAndStatusIn(
                salesOrderId, List.of(DeliveryStatus.DRAFT, DeliveryStatus.POSTED));
    }
}
