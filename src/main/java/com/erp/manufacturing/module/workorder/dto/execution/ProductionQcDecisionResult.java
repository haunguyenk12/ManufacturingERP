package com.erp.manufacturing.module.workorder.dto.execution;

/** G9c: answer of a QC decision command — the receipt as written (versions, totals, quality state) and the decision. */
public record ProductionQcDecisionResult(
        ProductionReceiptResponse receipt,
        ProductionQcDecisionResponse qcDecision
) {}
