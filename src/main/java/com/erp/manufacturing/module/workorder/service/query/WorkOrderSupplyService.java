package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderSupplyProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderSupplyService {

    private static final List<WorkOrderStatus> OPEN_SUPPLY_STATUSES = List.of(
            WorkOrderStatus.RELEASED,
            WorkOrderStatus.IN_PROGRESS);

    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getOpenSupplyQuantities(UUID companyId,
                                                         UUID plantId,
                                                         Collection<UUID> warehouseIds,
                                                         Collection<UUID> itemIds) {
        if (companyId == null || plantId == null || warehouseIds == null || warehouseIds.isEmpty()
                || itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return workOrderRepository.aggregateOpenSupply(
                        companyId, plantId, warehouseIds, itemIds, OPEN_SUPPLY_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        WorkOrderSupplyProjection::getItemId,
                        WorkOrderSupplyProjection::getOpenSupplyQuantity));
    }
}
