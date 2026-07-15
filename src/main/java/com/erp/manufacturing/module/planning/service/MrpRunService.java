package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.RequestContext;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.MrpRequirementLineResponse;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MrpRunService {

    private final MrpRunRepository mrpRunRepository;
    private final PlanningDemandRepository planningDemandRepository;
    private final MrpRunDemandRepository mrpRunDemandRepository;
    private final MrpRequirementLineRepository requirementLineRepository;
    private final SupplySuggestionRepository supplySuggestionRepository;
    private final OrganizationLookupService organizationLookupService;
    private final MrpCalculationService calculationService;
    private final MrpPlanningMapper mapper;
    private final AuditLogService auditLogService;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_RUN', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.MRP_RUN_CREATED, entityType = "MrpRun", entityIdExpression = "mrpRunId.toString()")
    public MrpRunResponse run(MrpRunCreateRequest request) {
        validateHorizon(request);
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);
        Warehouse warehouse = request.warehouseId() == null
                ? null
                : organizationLookupService.getActiveWarehouse(request.warehouseId());
        if (warehouse != null) {
            ensureWarehouseBelongsToPlant(warehouse, plant);
        }

        List<UUID> scopeWarehouseIds = resolveScopeWarehouseIds(plant, warehouse);
        MrpRun run = MrpRun.builder()
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .horizonStartDate(request.horizonStartDate())
                .horizonEndDate(request.horizonEndDate())
                .build();
        run.start(Instant.now());
        run = mrpRunRepository.save(run);
        MrpRun persistedRun = run;

        List<PlanningDemand> demands = planningDemandRepository.findOpenDemandsForRun(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse == null ? null : warehouse.getWarehouseId(),
                request.horizonStartDate(),
                request.horizonEndDate());
        mrpRunDemandRepository.saveAll(demands.stream()
                .map(demand -> snapshotDemand(persistedRun, demand))
                .toList());

        try {
            MrpCalculationService.MrpCalculationResult result = calculationService.calculate(
                    persistedRun, demands, scopeWarehouseIds);
            Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> savedRequirements =
                    persistRequirements(persistedRun, result.requirements());
            persistSuggestions(persistedRun, result.suggestions(), savedRequirements);
            persistedRun.complete(Instant.now(), demands.size(), result.requirements().size(), result.suggestions().size());
            auditRunOutcome(AuditAction.MRP_RUN_COMPLETED, persistedRun, null);
        } catch (RuntimeException e) {
            persistedRun.fail(Instant.now(), e.getMessage());
            auditRunOutcome(AuditAction.MRP_RUN_FAILED, persistedRun, e.getMessage());
        }

        return mapper.toResponse(mrpRunRepository.save(persistedRun));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_READ', 'PLANT', #plantId)")
    public PageResult<MrpRunResponse> list(UUID companyId,
                                           UUID plantId,
                                           UUID warehouseId,
                                           MrpRunStatus status,
                                           Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        if (warehouseId != null) {
            ensureWarehouseBelongsToPlant(organizationLookupService.getActiveWarehouse(warehouseId), plant);
        }
        return PageResult.from(mrpRunRepository.search(companyId, plantId, warehouseId, status, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public MrpRunResponse get(UUID runId) {
        return mapper.toResponse(findRun(runId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public PageResult<MrpRequirementLineResponse> listRequirements(UUID runId, Pageable pageable) {
        ensureRunExists(runId);
        return PageResult.from(requirementLineRepository.findByMrpRunMrpRunId(runId, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public PageResult<SupplySuggestionResponse> listSuggestions(UUID runId, Pageable pageable) {
        ensureRunExists(runId);
        return PageResult.from(supplySuggestionRepository.findByMrpRunMrpRunId(runId, pageable)
                .map(mapper::toResponse));
    }

    private MrpRunDemand snapshotDemand(MrpRun run, PlanningDemand demand) {
        return MrpRunDemand.builder()
                .mrpRun(run)
                .planningDemand(demand)
                .item(demand.getItem())
                .warehouse(demand.getWarehouse())
                .requiredQuantity(demand.getRequiredQuantity())
                .dueDate(demand.getDueDate())
                .priority(demand.getPriority())
                .build();
    }

    private Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> persistRequirements(
            MrpRun run,
            List<MrpCalculationService.RequirementDraft> drafts) {
        Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> saved = new IdentityHashMap<>();
        for (MrpCalculationService.RequirementDraft draft : drafts) {
            MrpRequirementLine line = MrpRequirementLine.builder()
                    .mrpRun(run)
                    .parentRequirementLine(draft.parent() == null ? null : saved.get(draft.parent()))
                    .sourceDemand(draft.sourceDemand())
                    .item(draft.item())
                    .warehouse(draft.warehouse())
                    .requirementLevel(draft.level())
                    .grossRequiredQuantity(draft.grossRequiredQuantity())
                    .availableQuantity(draft.availableQuantity())
                    .reservedQuantity(draft.reservedQuantity())
                    .openSupplyQuantity(draft.openSupplyQuantity())
                    .safetyStockQuantity(draft.safetyStockQuantity())
                    .netRequiredQuantity(draft.netRequiredQuantity())
                    .dueDate(draft.dueDate())
                    .requirementStatus(draft.status())
                    .note(draft.note())
                    .build();
            saved.put(draft, requirementLineRepository.save(line));
        }
        return saved;
    }

    private void persistSuggestions(MrpRun run,
                                    List<MrpCalculationService.SuggestionDraft> suggestions,
                                    Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> savedRequirements) {
        supplySuggestionRepository.saveAll(suggestions.stream()
                .map(draft -> {
                    MrpCalculationService.RequirementDraft requirement = draft.requirement();
                    MrpRequirementLine requirementLine = savedRequirements.get(requirement);
                    return SupplySuggestion.builder()
                            .mrpRun(run)
                            .requirementLine(requirementLine)
                            .company(run.getCompany())
                            .plant(run.getPlant())
                            .warehouse(requirement.warehouse())
                            .item(requirement.item())
                            .suggestionType(draft.suggestionType())
                            .suggestedQuantity(requirement.netRequiredQuantity())
                            .neededByDate(requirement.dueDate())
                            .suggestedOrderDate(requirement.suggestedOrderDate())
                            .build();
                })
                .toList());
    }

    private MrpRun findRun(UUID runId) {
        return mrpRunRepository.findWithDetailsByMrpRunId(runId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", runId));
    }

    private void ensureRunExists(UUID runId) {
        if (!mrpRunRepository.existsById(runId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", runId);
        }
    }

    private List<UUID> resolveScopeWarehouseIds(Plant plant, Warehouse warehouse) {
        if (warehouse != null) {
            return List.of(warehouse.getWarehouseId());
        }
        OrganizationScopeResolution scope = organizationLookupService.resolveScope(
                ScopeResourceType.PLANT, plant.getPlantId());
        return scope.warehouseIds();
    }

    private void validateHorizon(MrpRunCreateRequest request) {
        if (request.horizonStartDate().isAfter(request.horizonEndDate())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "MRP horizon start date cannot be after horizon end date");
        }
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Plant must belong to the selected company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Warehouse must belong to the selected plant");
        }
    }

    private void auditRunOutcome(AuditAction action, MrpRun run, String description) {
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return;
            }
            if (description == null) {
                auditLogService.logEntity(
                        RequestContext.capture(attrs.getRequest()), action, "MrpRun", run.getMrpRunId());
            } else {
                auditLogService.log(
                        RequestContext.capture(attrs.getRequest()), action, description);
            }
        } catch (RuntimeException ignored) {
            // Audit must not change the MRP business outcome.
        }
    }
}
