package com.erp.manufacturing.module.workorder.domain;

import com.erp.manufacturing.module.inventory.domain.LotStatus;

/**
 * Outcome of the QC decision taken on an approved production receipt (spec §6.1).
 * Each value maps onto the {@link LotStatus} the output lot ends up in.
 */
public enum QualityDispositionResult {

    AVAILABLE(LotStatus.AVAILABLE),
    REJECTED(LotStatus.REJECTED);

    private final LotStatus lotStatus;

    QualityDispositionResult(LotStatus lotStatus) {
        this.lotStatus = lotStatus;
    }

    public LotStatus lotStatus() {
        return lotStatus;
    }
}
