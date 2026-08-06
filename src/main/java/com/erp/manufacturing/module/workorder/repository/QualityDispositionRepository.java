package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.QualityDisposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QualityDispositionRepository extends JpaRepository<QualityDisposition, UUID> {

    List<QualityDisposition> findByReceiptReceiptId(UUID receiptId);

    /**
     * Whether a lot has ever been QC-dispositioned — used by {@code LotQcOriginLookupService}
     * (C2-2) alongside {@code ProductionReceiptLineRepository.existsByLotLotId} so a lot that was
     * properly dispositioned once, then later manually re-quarantined to {@code HOLD} via
     * {@code module/inventory}'s generic status-change endpoint, is not permanently blocked from
     * leaving {@code HOLD} again.
     */
    boolean existsByLotLotId(UUID lotId);
}
