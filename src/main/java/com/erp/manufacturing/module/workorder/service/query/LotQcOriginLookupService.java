package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Entry point for {@code module/inventory} (C2-2) to ask whether a lot must go through production QC before it may
 * leave {@code HOLD} via the generic {@code POST /inventory/lots/{lotId}/status} endpoint (rule C7 — a lookup
 * service, never a repository, is the only allowed way for another module to read this module's data).
 *
 * <p>G9c: the answer is "QC has not finished judging the receipt line that produced it" — the line's pending
 * quantity, not whether a verdict row exists. A lot QC finished with, then quarantined again by hand, stays freely
 * releasable. Child lots QC rejected carry {@link #LOT_SOURCE_TYPE} and are never released by hand.
 */
@Service
@RequiredArgsConstructor
public class LotQcOriginLookupService {

    /** {@code inventory_lots.source_reference_type} of a child lot split off by a production QC decision. */
    public static final String LOT_SOURCE_TYPE = "PRODUCTION_QC_DECISION";

    private final ProductionReceiptLineRepository productionReceiptLineRepository;

    @Transactional(readOnly = true)
    public boolean requiresQcDispositionBeforeRelease(UUID lotId) {
        return productionReceiptLineRepository.existsPendingQualityForLot(lotId, ProductionReceiptStatus.APPROVED);
    }
}
