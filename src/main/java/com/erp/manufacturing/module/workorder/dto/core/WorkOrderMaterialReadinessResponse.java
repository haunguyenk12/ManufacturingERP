package com.erp.manufacturing.module.workorder.dto.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkOrderMaterialReadinessResponse(
        UUID workOrderId,
        String workOrderNo,
        String status,
        boolean ready,
        /**
         * {@code ready} restated together with the state machine (spec §3.4): material can be fully
         * reserved and the work order still be un-releasable because it is already COMPLETED.
         */
        boolean canRelease,
        /** 0–100, share of the outstanding requirement covered by active reservations. */
        BigDecimal reservedPercent,
        int shortageLineCount,
        List<WorkOrderMaterialReadinessLineResponse> lines
) {}
