package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * G8d: where the material of each posted issue line went — consumed by executions, returned, or still unused — derived
 * from the issue lines, the consumption ledger (G8c) and the return lines. Three queries per work order (C14); nothing
 * here is stored, so it cannot drift from those ledgers (invariant B222).
 */
@Component
@RequiredArgsConstructor
class MaterialSourceLedger {

    private final MaterialIssueLineRepository issueLineRepository;
    private final ProductionExecutionConsumptionRepository consumptionRepository;
    private final MaterialReturnLineRepository returnLineRepository;

    /** Every posted issue line of the work order, in component then posting order. */
    List<WipMaterialLineResponse> lines(WorkOrder workOrder) {
        List<MaterialIssueLine> lines = issueLineRepository.findPostedByWorkOrder(workOrder.getWorkOrderId());
        if (lines.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = lines.stream().map(MaterialIssueLine::getIssueLineId).toList();
        Map<UUID, BigDecimal> consumed = consumed(ids);
        Map<UUID, BigDecimal> returned = returned(ids);
        Instant asOf = Instant.now();
        boolean known = workOrder.isOperationTracking();
        return lines.stream()
                .map(line -> row(line, known,
                        consumed.getOrDefault(line.getIssueLineId(), BigDecimal.ZERO),
                        returned.getOrDefault(line.getIssueLineId(), BigDecimal.ZERO), asOf))
                .toList();
    }

    Map<UUID, BigDecimal> consumed(Collection<UUID> issueLineIds) {
        return byRef(consumptionRepository.sumByIssueLineIds(issueLineIds));
    }

    Map<UUID, BigDecimal> returned(Collection<UUID> issueLineIds) {
        return byRef(returnLineRepository.sumBySourceIssueLineIds(issueLineIds));
    }

    /**
     * Why the source of an issue line cannot take material back right now, or {@code null} when it can (FE §1.4 step
     * 12, A31): the item and warehouse must be active, a lot must be {@code AVAILABLE} and not expired. A serial-tracked
     * line is not returned in Giai đoạn 8 — serials are out of scope (FE §1.2).
     */
    static String ineligibleReason(MaterialIssueLine line, Instant asOf) {
        if (line.getSerial() != null) {
            return "SERIAL_TRACKED";
        }
        if (!line.getItem().isActive()) {
            return "ITEM_INACTIVE";
        }
        if (!line.getWarehouse().isActive()) {
            return "WAREHOUSE_INACTIVE";
        }
        InventoryLot lot = line.getLot();
        if (lot != null) {
            if (lot.getStatus() != LotStatus.AVAILABLE) {
                return "LOT_" + lot.getStatus();
            }
            if (lot.getExpiresAt() != null && !lot.getExpiresAt().isAfter(asOf)) {
                return "LOT_EXPIRED";
            }
        }
        return null;
    }

    private static WipMaterialLineResponse row(MaterialIssueLine line, boolean known, BigDecimal consumed,
                                               BigDecimal returned, Instant asOf) {
        BigDecimal unused = known ? line.getQuantity().subtract(consumed).subtract(returned) : null;
        String ineligible = ineligibleReason(line, asOf);
        BigDecimal returnable = unused == null ? null : (ineligible == null ? unused : BigDecimal.ZERO);
        InventoryLot lot = line.getLot();
        return new WipMaterialLineResponse(
                line.getIssueLineId(),
                line.getIssue().getIssueId(),
                line.getIssue().getCode(),
                line.getIssue().getPostedAt(),
                line.getComponentLine().getComponentLineId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                line.getWarehouse().getWarehouseId(),
                line.getWarehouse().getCode(),
                lot == null ? null : lot.getLotId(),
                lot == null ? null : lot.getLotCode(),
                line.getQuantity(),
                known,
                known ? consumed : null,
                returned,
                unused,
                returnable,
                ineligible == null,
                ineligible);
    }

    private static Map<UUID, BigDecimal> byRef(List<KeyedQuantityProjection> rows) {
        return rows.stream().collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
    }
}
