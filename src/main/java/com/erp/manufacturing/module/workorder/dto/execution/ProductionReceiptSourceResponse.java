package com.erp.manufacturing.module.workorder.dto.execution;

import com.erp.manufacturing.module.workorder.dto.wip.WipMaterialLineResponse;

import java.util.List;
import java.util.UUID;

/**
 * G9b (FE R13, Q27): where the output of one receipt came from, in two layers.
 *
 * <ul>
 *   <li><b>Output:</b> the final-good executions the receipt draws from ({@code contributions}). {@code null} with
 *       {@code outputEvidenceKnown = false} for a receipt written before V84 — nobody recorded them, and none is
 *       invented (R22).</li>
 *   <li><b>Material:</b> the posted issue lines of the work order with what was consumed and returned —
 *       {@code materialAttribution = WORK_ORDER_LEVEL}: material is not allocated per finished unit, so the whole
 *       work order's material is shown, never a share of it per receipt. {@code UNKNOWN} when the work order was
 *       reported before consumption was tracked (V81): the lines are listed, consumption is {@code null}.</li>
 * </ul>
 */
public record ProductionReceiptSourceResponse(
        UUID receiptId,
        String receiptCode,
        UUID workOrderId,
        String workOrderCode,
        boolean outputEvidenceKnown,
        String outputUnknownReason,
        List<ProductionReceiptContributionResponse> contributions,
        String materialAttribution,
        List<WipMaterialLineResponse> materials
) {}
