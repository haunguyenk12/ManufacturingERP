package com.erp.manufacturing.module.workorder.dto.cost;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.CostTotals;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.ActorRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G11b/G11c: the production cost of a work order (FE §3.3). The same records are the wire shape and the immutable
 * snapshot stored on a run, so a finalized run reads back exactly what was decided at close (FE R26).
 *
 * <p>Inside a financial object an unknown amount is {@code null} (FE Q6, written because
 * {@code default-property-inclusion: always}); an object the caller may not see is <b>absent</b> — {@code NON_NULL} on
 * that component only (the {@code G10a} pattern, B240).
 */
public final class ProductionCostDtos {

    private ProductionCostDtos() {
    }

    public enum Readiness { INCOMPLETE, BLOCKED, READY, STALE, CLOSED }

    public enum Section { OVERVIEW, MATERIALS, OPERATIONS, VARIANCE, ALLOCATION }

    /** Minimal reference to the document that has to be handled (FE §3.3 {@code checks[].source}). */
    public record SourceRef(String type, UUID id, String code) {
    }

    public record CostCheck(String code, boolean ok, List<String> reasonCodes, SourceRef source) {
    }

    public record Quantities(BigDecimal plannedQuantity, BigDecimal finalGoodQuantity, BigDecimal productionScrapQuantity,
                             BigDecimal historicalReworkQuantity, BigDecimal shortfallQuantity,
                             BigDecimal approvedReceiptQuantity, BigDecimal qcAcceptedQuantity,
                             BigDecimal qcRejectedQuantity, BigDecimal qcPendingQuantity) {
    }

    public record MaterialReturnValue(UUID returnId, String returnCode, UUID returnLineId, UUID movementId,
                                      BigDecimal quantity, BigDecimal value, Instant postedAt) {
    }

    /** One posted issue line: {@code issued = consumed + returned + unused}, in quantity and in value (FE §3.5 #4). */
    public record MaterialLine(UUID issueId, String issueCode, UUID issueLineId, UUID movementId, UUID componentLineId,
                               UUID itemId, String itemCode, UUID warehouseId, UUID lotId, String uom,
                               BigDecimal issuedQuantity, BigDecimal consumedQuantity, BigDecimal returnedQuantity,
                               BigDecimal unusedQuantity, BigDecimal unitCost, BigDecimal issuedValue,
                               BigDecimal consumedValue, BigDecimal returnedValue, BigDecimal unusedValue,
                               List<MaterialReturnValue> returns) {
    }

    public record ExecutionTime(UUID executionId, String code, UUID sourceExecutionId, Instant startedAt,
                                Instant endedAt, BigDecimal durationMinutes, boolean valid) {
    }

    public record OperationCost(UUID operationId, Integer sequence, String name, UUID workCenterId,
                                Integer capacityUnits, Integer rateVersion, BigDecimal laborRate,
                                BigDecimal overheadRate, BigDecimal plannedMinutes, BigDecimal actualMinutes,
                                BigDecimal plannedLabor, BigDecimal plannedOverhead, BigDecimal actualLabor,
                                BigDecimal actualOverhead, List<ExecutionTime> executions) {
    }

    public record DeliveryAllocation(UUID deliveryId, String deliveryCode, UUID deliveryLineId, UUID movementId,
                                     BigDecimal quantity, BigDecimal amount) {
    }

    /** A QC part of a receipt: ACCEPTED / REJECTED of one decision, or what is still PENDING. */
    public record PartAllocation(String quality, UUID decisionId, UUID lotId, BigDecimal quantity, BigDecimal amount,
                                 BigDecimal remainingQuantity, BigDecimal remainingValue,
                                 List<DeliveryAllocation> deliveries) {
    }

    public record ReceiptAllocation(UUID receiptId, String receiptCode, UUID warehouseId, Instant approvedAt,
                                    BigDecimal quantity, BigDecimal amount, List<PartAllocation> parts) {
    }

    /** {@code actual − planned} per cost element; {@code null} when either side is unknown. */
    public record VarianceSplit(BigDecimal material, BigDecimal labor, BigDecimal overhead, BigDecimal total) {
    }

    /** The full calculation; the run snapshot. */
    public record CostSnapshot(Quantities quantities, String currency, CostTotals planned, CostTotals actual,
                               VarianceSplit variance, BigDecimal variancePercent, BigDecimal plannedMinutes,
                               BigDecimal actualMinutes, BigDecimal unitCost, BigDecimal loss,
                               BigDecimal remainingValue, BigDecimal issuedValue, List<String> unknownReasons,
                               List<MaterialLine> materials, List<OperationCost> operations,
                               List<ReceiptAllocation> allocations, List<CostCheck> checks) {
    }

    /** C4 {@code section=overview} (FE E5). */
    public record Overview(BigDecimal plannedQuantity, BigDecimal finalGoodQuantity, BigDecimal productionScrapQuantity,
                           BigDecimal historicalReworkQuantity, BigDecimal shortfallQuantity,
                           BigDecimal approvedReceiptQuantity, BigDecimal qcAcceptedQuantity,
                           BigDecimal qcRejectedQuantity, BigDecimal qcPendingQuantity, String currency,
                           CostTotals planned, CostTotals actual, BigDecimal variance, BigDecimal variancePercent,
                           BigDecimal unitCost, BigDecimal loss, BigDecimal remainingValue, BigDecimal issuedValue,
                           List<String> unknownReasons) {
    }

    /** C4 {@code section=variance}. */
    public record VarianceView(CostTotals planned, CostTotals actual, VarianceSplit variance,
                               BigDecimal variancePercent, BigDecimal plannedQuantity, BigDecimal finalGoodQuantity,
                               BigDecimal plannedMinutes, BigDecimal actualMinutes) {
    }

    /** The audit of a close (FE §3.3 {@code closed}); {@code null} before the work order is closed. */
    public record ClosedInfo(Instant closedAt, ActorRef closedBy, String closeReason, UUID finalRunId) {
    }

    /** C4/C9 header + one section ({@code data}: an object or a page). */
    public record CostView(UUID workOrderId, String workOrderNo, UUID companyId, UUID plantId, UUID productItemId,
                           String productItemCode, String productItemName, String outputUom, WorkOrderStatus status,
                           Long workOrderVersion, Instant createdAt, String timeZone, Readiness readiness,
                           boolean moneyVisible, String sourceVersion, List<String> allowedActions, Section section,
                           Object data, ClosedInfo closed) {
    }

    /**
     * C6 checklist. A reader without cost view gets quantities and checks only: {@code sourceVersion} and {@code cost}
     * are absent, never {@code null} (FE R27, E6).
     */
    public record Checklist(UUID workOrderId, String workOrderNo, WorkOrderStatus status, Long workOrderVersion,
                            Readiness readiness, boolean moneyVisible, List<String> allowedActions,
                            Quantities quantities, List<CostCheck> checks,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String sourceVersion,
                            @JsonInclude(JsonInclude.Include.NON_NULL) ChecklistCost cost,
                            ClosedInfo closed) {
    }

    public record ChecklistCost(CostTotals planned, CostTotals actual, BigDecimal variance, UUID latestRunId,
                                Integer latestRunVersion) {
    }

    /** C3 row; money absent for an operational reader (mode=close without cost view). */
    public record ListRow(UUID workOrderId, String workOrderNo, WorkOrderStatus status, UUID productItemId,
                          String productItemCode, String productItemName, String outputUom,
                          BigDecimal plannedQuantity, BigDecimal finalGoodQuantity, Instant createdAt,
                          Long workOrderVersion, Readiness readiness, boolean moneyVisible,
                          @JsonInclude(JsonInclude.Include.NON_NULL) CostTotals planned,
                          @JsonInclude(JsonInclude.Include.NON_NULL) CostTotals actual,
                          @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal variance) {
    }

    /** C7 body. */
    public record RunRequest(@NotNull @Min(0) Long expectedVersion, @NotBlank String expectedSourceVersion) {
    }

    /** C7/C10 success: identity and versions only (FE E4, E8). */
    public record RunCommandResult(UUID workOrderId, UUID runId, Integer runVersion, Long workOrderVersion) {
    }

    /** C8 row. */
    public record RunSummary(UUID runId, String runNo, Integer runVersion, String state, String sourceVersion,
                             Long workOrderVersion, BigDecimal plannedTotal, BigDecimal actualTotal, ActorRef actor,
                             Instant at, Instant finalizedAt, ActorRef finalizedBy, String closeReason) {
    }

    /** C10 body. */
    public record FinalizeRequest(@NotNull @Min(0) Long expectedVersion, @NotBlank String expectedSourceVersion,
                                  @NotNull UUID runId, @NotNull @Min(1) Integer expectedRunVersion, String reason) {
    }

    /** C12 row: one value-only entry (quantity is always 0, FE E9). */
    public record Adjustment(UUID adjustmentId, String type, UUID companyId, UUID plantId, UUID workOrderId, UUID runId,
                             UUID itemId, UUID warehouseId, UUID receiptId, UUID lotId, String quality,
                             UUID deliveryId, UUID deliveryLineId, UUID adjustedMovementId, BigDecimal quantity,
                             BigDecimal stockValueDelta, BigDecimal allocatedAmount, BigDecimal stockValueBefore,
                             BigDecimal stockValueAfter, Instant at, ActorRef actor, String reason) {
    }

    /** C11 line: the production cost of one delivery line, {@code null} amount while unknown. */
    public record DeliveryCostLine(UUID deliveryLineId, Integer lineNo, UUID itemId, UUID lotId, BigDecimal quantity,
                                   BigDecimal amount, boolean known, List<UUID> adjustmentIds, String unknownReason) {
    }

    public record DeliveryCost(UUID deliveryId, String deliveryCode, String currency, BigDecimal knownAmount,
                               boolean complete, List<DeliveryCostLine> lines) {
    }
}
