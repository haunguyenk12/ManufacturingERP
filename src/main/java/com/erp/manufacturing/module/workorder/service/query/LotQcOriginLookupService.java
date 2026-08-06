package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.QualityDispositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Entry point for {@code module/inventory} (C2-2) to ask whether a lot must go through QC
 * disposition before it may leave {@code HOLD} via the generic
 * {@code POST /inventory/lots/{lotId}/status} endpoint (rule C7 — a lookup service, never a
 * repository, is the only allowed way for another module to read this module's data).
 * <p>
 * A lot only needs this gate if it was produced by a production receipt line and has never been
 * QC-dispositioned. A same-module heuristic in {@code inventory} (inferring origin from the
 * earliest {@code RECEIVE} {@code StockMovement.referenceType}) cannot distinguish this from a lot
 * that was properly dispositioned once and later manually re-quarantined to {@code HOLD} — that lot
 * must remain freely releasable, which is exactly what checking {@code QualityDisposition} existence
 * (not just origin) gets right.
 */
@Service
@RequiredArgsConstructor
public class LotQcOriginLookupService {

    private final ProductionReceiptLineRepository productionReceiptLineRepository;
    private final QualityDispositionRepository qualityDispositionRepository;

    @Transactional(readOnly = true)
    public boolean requiresQcDispositionBeforeRelease(UUID lotId) {
        return productionReceiptLineRepository.existsByLotLotId(lotId)
                && !qualityDispositionRepository.existsByLotLotId(lotId);
    }
}
