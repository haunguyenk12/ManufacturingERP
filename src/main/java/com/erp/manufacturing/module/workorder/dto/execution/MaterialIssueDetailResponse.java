package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /material-issues/{id}} (G8b): the issue plus, per component it touches, what a manager needs
 * to decide on it (FE §3.2 "decision context").
 */
public record MaterialIssueDetailResponse(
        MaterialIssueResponse issue,
        List<DecisionContext> decisionContext
) {

    /**
     * @param grossIssuedQuantity    everything posted to the component so far; returns never reduce it (FE Q11)
     * @param batchRequestedQuantity what this document asks for on the component
     * @param extraRequestedQuantity {@code max(0, gross + batch − required)} while the issue is pending;
     *                               {@code null} once decided — by then gross already contains the batch
     * @param currentAvailability    stock the document's sources hold now — a snapshot, approval re-checks
     */
    public record DecisionContext(UUID componentLineId, UUID itemId, String itemCode, String uom,
                                  BigDecimal requiredQuantity, BigDecimal grossIssuedQuantity,
                                  BigDecimal batchRequestedQuantity, BigDecimal extraRequestedQuantity,
                                  BigDecimal currentAvailability, Instant asOf) {}
}
