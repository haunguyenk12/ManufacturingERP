package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueCandidateResponse;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Where a component can be issued from (G8b, FE §3.3). Read-only.
 *
 * <ul>
 *   <li>{@code NORMAL} — the work order's own {@code ACTIVE} reservations with something left, each with the
 *       quick-fill amount {@code min(left, required − gross issued)} (FE §3.1). A reservation whose lot has
 *       since gone on hold or expired is listed as ineligible rather than hidden, so the screen can say why.</li>
 *   <li>{@code SUPPLEMENTAL} — free stock of the component in every warehouse of the plant (FE Q8), lots
 *       {@code AVAILABLE} and not expired only, earliest expiry first.</li>
 * </ul>
 *
 * The page is cut on the server from the complete list (FE R21), ordered by component line then source.
 */
@Service
@RequiredArgsConstructor
public class MaterialIssueCandidateService {

    public enum Mode { NORMAL, SUPPLEMENTAL }

    private final MaterialReservationRepository reservationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final WorkOrderExecutionSupport support;

    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    public PageResult<MaterialIssueCandidateResponse> candidates(UUID workOrderId, UUID componentLineId, Mode mode,
                                                                 Pageable pageable) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        List<WorkOrderComponentLine> components = workOrder.getComponentLines().stream()
                .filter(line -> componentLineId == null || line.getComponentLineId().equals(componentLineId))
                .sorted(Comparator.comparing(WorkOrderComponentLine::getLineNo))
                .toList();
        if (componentLineId != null && components.isEmpty()) {
            support.findComponentLine(workOrder, componentLineId);
        }
        Instant asOf = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<MaterialIssueCandidateResponse> rows = mode == Mode.SUPPLEMENTAL
                ? freeStock(workOrder, components, asOf)
                : reservations(workOrderId, components, asOf);
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return PageResult.from(new PageImpl<>(rows.subList(from, to), pageable, rows.size()));
    }

    private List<MaterialIssueCandidateResponse> reservations(UUID workOrderId, List<WorkOrderComponentLine> components,
                                                              Instant asOf) {
        List<MaterialReservation> active = reservationRepository
                .findByWorkOrderWorkOrderIdAndStatus(workOrderId, MaterialReservationStatus.ACTIVE);
        List<MaterialIssueCandidateResponse> rows = new ArrayList<>();
        for (WorkOrderComponentLine component : components) {
            BigDecimal stillNeeded = component.getRequiredQuantity().subtract(component.getIssuedQuantity()).max(BigDecimal.ZERO);
            for (MaterialReservation reservation : active) {
                if (!reservation.getComponentLine().getComponentLineId().equals(component.getComponentLineId())
                        || reservation.remainingQuantity().signum() <= 0) {
                    continue;
                }
                String ineligible = ineligibleReason(reservation.getLot(), asOf);
                BigDecimal suggested = ineligible != null ? BigDecimal.ZERO : reservation.remainingQuantity().min(stillNeeded);
                rows.add(row(component, "RESERVATION", reservation.getReservationId(),
                        reservation.getWarehouse().getWarehouseId(), reservation.getWarehouse().getCode(),
                        reservation.getLot(), reservation.remainingQuantity(), ineligible, suggested, asOf));
            }
        }
        return rows;
    }

    private List<MaterialIssueCandidateResponse> freeStock(WorkOrder workOrder, List<WorkOrderComponentLine> components,
                                                           Instant asOf) {
        List<UUID> warehouseIds = support.plantWarehouseIds(workOrder.getPlant());
        List<MaterialIssueCandidateResponse> rows = new ArrayList<>();
        if (warehouseIds.isEmpty()) {
            return rows;
        }
        for (WorkOrderComponentLine component : components) {
            for (StockBalance balance : stockBalanceRepository.findReservableBalancesFefo(
                    component.getComponentItem().getItemId(), warehouseIds, LotStatus.AVAILABLE, asOf,
                    Pageable.unpaged()).getContent()) {
                rows.add(row(component, "FREE_STOCK", null, balance.getWarehouse().getWarehouseId(),
                        balance.getWarehouse().getCode(), balance.getLot(), balance.availableQuantity(), null, null, asOf));
            }
        }
        return rows;
    }

    private static String ineligibleReason(InventoryLot lot, Instant asOf) {
        if (lot == null) {
            return null;
        }
        if (lot.getStatus() != LotStatus.AVAILABLE) {
            return "LOT_" + lot.getStatus();
        }
        if (lot.getExpiresAt() != null && !lot.getExpiresAt().isAfter(asOf)) {
            return "LOT_EXPIRED";
        }
        return null;
    }

    private static MaterialIssueCandidateResponse row(WorkOrderComponentLine component, String sourceType,
                                                      UUID reservationId, UUID warehouseId, String warehouseCode,
                                                      InventoryLot lot, BigDecimal available, String ineligible,
                                                      BigDecimal suggested, Instant asOf) {
        return new MaterialIssueCandidateResponse(component.getComponentLineId(), component.getLineNo(),
                component.getComponentItem().getItemId(), component.getComponentItem().getCode(),
                component.getComponentItem().getName(), component.getComponentItem().getUnit(),
                component.getRequiredQuantity(), component.getIssuedQuantity(), sourceType, reservationId,
                warehouseId, warehouseCode, lot == null ? null : lot.getLotId(), lot == null ? null : lot.getLotCode(),
                lot == null ? null : lot.getExpiresAt(), available, ineligible == null, ineligible, suggested, asOf);
    }
}
