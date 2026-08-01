package com.erp.manufacturing.module.planning.mapper;

import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.*;
import org.springframework.stereotype.Component;

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
                run.getUpdatedAt());
    }

    public MrpRequirementLineResponse toResponse(MrpRequirementLine line) {
        Warehouse warehouse = line.getWarehouse();
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
                line.getExcludedLotCount(),
                line.getNote(),
                line.getCreatedAt());
    }

    public SupplySuggestionResponse toResponse(SupplySuggestion suggestion) {
        Warehouse warehouse = suggestion.getWarehouse();
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
