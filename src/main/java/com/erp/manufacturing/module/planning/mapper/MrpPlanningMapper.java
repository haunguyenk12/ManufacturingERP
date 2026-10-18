package com.erp.manufacturing.module.planning.mapper;

import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class MrpPlanningMapper {

    public PlanningDemandResponse toResponse(PlanningDemand demand) {
        Warehouse warehouse = demand.getWarehouse();
        return new PlanningDemandResponse(
                demand.getPlanningDemandId(),
                demand.getCompany().getCompanyId(),
                demand.getCompany().getCode(),
                demand.getPlant().getPlantId(),
                demand.getPlant().getCode(),
                demand.getItem().getItemId(),
                demand.getItem().getCode(),
                demand.getItem().getName(),
                warehouse == null ? null : warehouse.getWarehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                demand.getDemandType().name(),
                demand.getRequiredQuantity(),
                demand.getDueDate(),
                demand.getPriority(),
                demand.getStatus().name(),
                demand.getReferenceType(),
                demand.getReferenceId(),
                demand.getCreatedAt(),
                demand.getUpdatedAt());
    }

    public MrpRunResponse toResponse(MrpRun run) {
        return toResponse(run, null);
    }

    /** @param currentSourceState {@code CURRENT}/{@code STALE}, computed by the caller on detail reads only. */
    public MrpRunResponse toResponse(MrpRun run, String currentSourceState) {
        Warehouse warehouse = run.getWarehouse();
        return new MrpRunResponse(
                run.getMrpRunId(),
                run.getCode(),
                run.getCompany().getCompanyId(),
                run.getCompany().getCode(),
                run.getPlant().getPlantId(),
                run.getPlant().getCode(),
                warehouse == null ? null : warehouse.getWarehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                run.getHorizonStartDate(),
                run.getHorizonEndDate(),
                run.getStatus().name(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getTotalDemandLines(),
                run.getTotalRequirementLines(),
                run.getTotalSuggestionLines(),
                run.getGrossDemandQuantity(),
                run.getShortageLines(),
                run.getPlannedWorkOrders(),
                run.getPlannedPurchaseRecommendations(),
                run.getBlockedProposals(),
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getRunKind().name(),
                run.getSourceVersion(),
                currentSourceState,
                run.getConversionVersion(),
                run.getInputSnapshot());
    }

    public MrpRequirementLineResponse toResponse(MrpRequirementLine line) {
        return toResponse(line, null, BigDecimal.ZERO, null);
    }

    /**
     * @param suggestion   the proposal of this line, or {@code null} when nothing is to be supplied
     * @param ledgerSum    quantity already converted through the G6b ledger
     * @param conversions  ledger rows, only for the detail read
     */
    public MrpRequirementLineResponse toResponse(MrpRequirementLine line,
                                                 SupplySuggestion suggestion,
                                                 BigDecimal ledgerSum,
                                                 List<PlanningConversionResponse> conversions) {
        return toResponse(line, suggestion, ledgerSum, ledgerSum != null && ledgerSum.signum() > 0, conversions);
    }

    /** @param hasLedgerRows whether any ledger row (released or not) exists for the proposal */
    public MrpRequirementLineResponse toResponse(MrpRequirementLine line,
                                                 SupplySuggestion suggestion,
                                                 BigDecimal ledgerSum,
                                                 boolean hasLedgerRows,
                                                 List<PlanningConversionResponse> conversions) {
        Warehouse warehouse = line.getWarehouse();
        BigDecimal converted = suggestion == null ? null : convertedQuantity(suggestion, ledgerSum, hasLedgerRows);
        MrpRequirementLine parent = line.getParentRequirementLine();
        return new MrpRequirementLineResponse(
                line.getMrpRequirementLineId(),
                line.getMrpRun().getMrpRunId(),
                line.getParentRequirementLine() == null ? null : line.getParentRequirementLine().getMrpRequirementLineId(),
                line.getSourceDemand() == null ? null : line.getSourceDemand().getPlanningDemandId(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                warehouse == null ? null : warehouse.getWarehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                line.getRequirementLevel(),
                line.getGrossRequiredQuantity(),
                line.getAvailableQuantity(),
                line.getReservedQuantity(),
                line.getOpenSupplyQuantity(),
                line.getSafetyStockQuantity(),
                line.getProjectedAvailableQuantity(),
                line.getNetRequiredQuantity(),
                line.getDueDate(),
                line.getRequirementStatus().name(),
                line.getSettingSource().name(),
                line.getWarehouseResolutionSource().name(),
                line.getExcludedLotCount(),
                line.getNote(),
                line.getCreatedAt(),
                sourceKind(line),
                sourceSalesOrderLineIds(line.getSourceDemand()),
                parent == null ? List.of() : List.of(parent.getMrpRequirementLineId()),
                suggestion != null && suggestion.isBlocked() ? "BLOCKED" : "CALCULATED",
                suggestion == null ? null : suggestion.getSupplySuggestionId(),
                suggestion == null ? null : suggestion.getSuggestionType().supplyType(),
                suggestion == null ? null : suggestion.getExceptionState().name(),
                suggestion == null ? List.of() : suggestion.messages(),
                converted,
                converted == null ? null : remaining(suggestion.getSuggestedQuantity(), converted),
                conversions);
    }

    public PlanningConversionResponse toResponse(SupplySuggestionConversion conversion) {
        return new PlanningConversionResponse(
                conversion.getConversionId(),
                conversion.getConversionBatchId(),
                conversion.getMrpRunId(),
                conversion.getRequirementLineId(),
                conversion.getSupplySuggestionId(),
                conversion.getEntryNo(),
                conversion.getQuantity(),
                conversion.getTargetType().name(),
                conversion.getTargetId(),
                conversion.getTargetLineId(),
                conversion.getTargetCode(),
                conversion.getConversionVersion(),
                conversion.getCreatedAt(),
                conversion.getCreatedBy(),
                conversion.getReleasedAt(),
                conversion.getReleaseReason());
    }

    /**
     * Quantity of a proposal already turned into documents. A proposal converted through the
     * legacy endpoint (status {@code CONVERTED}, no ledger row) converted all of it in one go.
     */
    public static BigDecimal convertedQuantity(SupplySuggestion suggestion, BigDecimal ledgerSum) {
        return convertedQuantity(suggestion, ledgerSum, ledgerSum != null && ledgerSum.signum() > 0);
    }

    /**
     * A proposal {@code CONVERTED} by the legacy one-shot endpoints has no ledger rows and counts as
     * fully converted. One the ledger touched counts only its held rows — so a purchase order
     * cancelled since (G7a, released rows) gives its quantity back even though the proposal stays
     * {@code CONVERTED}.
     */
    public static BigDecimal convertedQuantity(SupplySuggestion suggestion, BigDecimal heldSum, boolean hasLedgerRows) {
        BigDecimal sum = heldSum == null ? BigDecimal.ZERO : heldSum;
        if (!hasLedgerRows && suggestion.getStatus() == SupplySuggestionStatus.CONVERTED) {
            return suggestion.getSuggestedQuantity();
        }
        return sum;
    }

    public static BigDecimal remaining(BigDecimal suggested, BigDecimal converted) {
        BigDecimal rest = suggested.subtract(converted);
        return rest.signum() > 0 ? rest : BigDecimal.ZERO;
    }

    private static String sourceKind(MrpRequirementLine line) {
        if (line.getRequirementLevel() != null && line.getRequirementLevel() > 0) {
            return "COMPONENT";
        }
        PlanningDemand demand = line.getSourceDemand();
        if (demand == null) {
            return null;
        }
        return demand.getDemandType() == PlanningDemandType.SALES_ORDER ? "SALES" : demand.getDemandType().name();
    }

    private static List<UUID> sourceSalesOrderLineIds(PlanningDemand demand) {
        if (demand == null
                || !"SALES_ORDER_LINE".equals(demand.getReferenceType())
                || demand.getReferenceId() == null) {
            return List.of();
        }
        try {
            return List.of(UUID.fromString(demand.getReferenceId()));
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    public SupplySuggestionResponse toResponse(SupplySuggestion suggestion) {
        Warehouse warehouse = suggestion.getWarehouse();
        Warehouse outputWarehouse = suggestion.getOutputWarehouse();
        Warehouse receivingWarehouse = suggestion.getReceivingWarehouse();
        return new SupplySuggestionResponse(
                suggestion.getSupplySuggestionId(),
                suggestion.getMrpRun().getMrpRunId(),
                suggestion.getRequirementLine().getMrpRequirementLineId(),
                suggestion.getCompany().getCompanyId(),
                suggestion.getCompany().getCode(),
                suggestion.getPlant().getPlantId(),
                suggestion.getPlant().getCode(),
                warehouse == null ? null : warehouse.getWarehouseId(),
                warehouse == null ? null : warehouse.getCode(),
                outputWarehouse == null ? null : outputWarehouse.getWarehouseId(),
                outputWarehouse == null ? null : outputWarehouse.getCode(),
                receivingWarehouse == null ? null : receivingWarehouse.getWarehouseId(),
                receivingWarehouse == null ? null : receivingWarehouse.getCode(),
                suggestion.getItem().getItemId(),
                suggestion.getItem().getCode(),
                suggestion.getItem().getName(),
                suggestion.getItem().getUnit(),
                suggestion.getSuggestionType().supplyType(),
                suggestion.getSuggestedQuantity(),
                suggestion.getNeededByDate(),
                suggestion.getSuggestedOrderDate(),
                suggestion.getSourceRoutingCode(),
                suggestion.getSourceRoutingVersion(),
                suggestion.getStatus().name(),
                suggestion.getExceptionState().name(),
                suggestion.messages(),
                suggestion.getDecisionNote(),
                suggestion.getConvertedReferenceType(),
                suggestion.getConvertedReferenceId(),
                suggestion.convertedWorkOrderId(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt());
    }
}
