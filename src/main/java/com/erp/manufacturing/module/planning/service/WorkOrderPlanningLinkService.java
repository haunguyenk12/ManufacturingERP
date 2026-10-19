package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * G8a: the work order side of the G6b conversion ledger (rule C7 — workorder never reads the ledger
 * repository itself). Gives a cancelled work order's planning quota back, exactly as
 * {@link PurchaseOrderPlanningLinkService} does for a purchase order (FE R06/Q21).
 *
 * <p>No {@code @PreAuthorize}: the only caller is {@code WorkOrderService.cancel}, already authorised on
 * the work order. Not called from a controller.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderPlanningLinkService {

    private final SupplySuggestionConversionRepository conversionRepository;
    private final MrpRunRepository mrpRunRepository;

    /**
     * Marks every ledger row held by this work order released — never deleted — and moves each source
     * run's conversion version on. Runs in the caller's transaction; a second call finds nothing held
     * ("once"). A work order converted by the one-shot path before G6b has no ledger row and releases
     * nothing — the same rule G7a applies to purchase orders.
     *
     * @return the number of rows released
     */
    @Transactional
    public int releaseWorkOrder(UUID workOrderId, String reason, Instant at, UUID by) {
        return PurchaseOrderPlanningLinkService.releaseTarget(SupplyConversionTargetType.WORK_ORDER, workOrderId,
                reason, at, by, conversionRepository, mrpRunRepository);
    }
}
