package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.RequestContext;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
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
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final IdempotencySupport idempotency;

    /**
     * Runs MRP synchronously.
     *
     * @param idempotencyKey optional {@code Idempotency-Key} header (V58). Absent ⇒ behaviour is
     *        exactly what it always was: every call starts a new run. Present ⇒ the key is claimed
     *        by the run row, and replaying it returns that same run instead of computing a second
     *        one over the same demand.
     *        <p>Two consequences that are deliberate and documented for clients:
     *        <ul>
     *          <li>A run that ends {@code FAILED} still owns its key, because the row is written
     *              before the calculation and this method records failure rather than rethrowing.
     *              Retrying after a failure therefore needs a <em>new</em> key.</li>
     *          <li>Two concurrent submissions of one key do not queue: the second loses the race on
     *              {@code uk_mrp_runs_idempotency_key} and fails fast with
     *              {@code RESOURCE_ALREADY_EXISTS}, rather than blocking for the whole calculation.</li>
     *        </ul>
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_RUN', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.MRP_RUN_CREATED, entityType = "MrpRun", entityIdExpression = "mrpRunId.toString()")
    public MrpRunResponse run(MrpRunCreateRequest request, String idempotencyKey) {
        String normalizedKey = StringUtils.hasText(idempotencyKey)
                ? idempotency.normalizeKey(idempotencyKey)
                : null;
        if (normalizedKey != null) {
            Optional<MrpRun> replayed = mrpRunRepository.findByIdempotencyKey(normalizedKey);
            if (replayed.isPresent()) {
                idempotency.ensureSamePayload(replayed.get().getPayloadHash(), request);
                return mapper.toResponse(replayed.get());
            }
        }
        validateHorizon(request);
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);
        UUID demandWarehouseId = request.effectiveDemandWarehouseId();
        if (request.warehouseId() != null && request.demandWarehouseId() != null
                && !request.warehouseId().equals(request.demandWarehouseId())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "warehouseId and demandWarehouseId must match when both are supplied");
        }
        Warehouse warehouse = demandWarehouseId == null
                ? null
                : organizationLookupService.getActiveWarehouse(demandWarehouseId);
        if (warehouse != null) {
            ensureWarehouseBelongsToPlant(warehouse, plant);
        }

        // Resolved before the run row exists so an unusable selection fails as 404/409 instead of
        // leaving a FAILED run behind (rule C9).
        List<PlanningDemand> demands = resolveDemands(request, company, plant, warehouse);

        List<UUID> scopeWarehouseIds = resolveScopeWarehouseIds(plant, warehouse);
        MrpRun run = MrpRun.builder()
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .horizonStartDate(request.horizonStartDate())
                .horizonEndDate(request.horizonEndDate())
                .idempotencyKey(normalizedKey)
                .payloadHash(normalizedKey == null ? null : idempotency.payloadHash(request))
                .build();
        run.start(Instant.now());
        // saveAndFlush, not save: the INSERT has to hit the database now so the key is claimed
        // before the calculation starts. With a deferred flush a concurrent duplicate would run the
        // whole thing and only collide at commit, wasting the work it was sent to prevent.
        run = mrpRunRepository.saveAndFlush(run);
        MrpRun persistedRun = run;

        mrpRunDemandRepository.saveAll(demands.stream()
                .map(demand -> snapshotDemand(persistedRun, demand))
                .toList());

        try {
            MrpCalculationService.MrpCalculationResult result = calculationService.calculate(
                    persistedRun, demands, scopeWarehouseIds);
            Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> savedRequirements =
                    persistRequirements(persistedRun, result.requirements());
            persistSuggestions(persistedRun, result.suggestions(), savedRequirements);
            persistedRun.complete(
                    Instant.now(),
                    demands.size(),
                    result.requirements().size(),
                    result.suggestions().size(),
                    sumGrossDemand(result),
                    countShortageLines(result),
                    countSuggestions(result, SupplySuggestionType.WORK_ORDER),
                    countSuggestions(result, SupplySuggestionType.PURCHASE_REQUISITION),
                    countBlockedProposals(result));
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

    /**
     * The demands that go into this run. An explicit {@code demandLineIds} selection (spec §2.3)
     * wins over the horizon sweep: the planner already filtered by due date on the demand screen, so
     * re-applying the horizon here would silently drop lines they deliberately picked. Omitting the
     * field keeps the pre-F5-B sweep so existing clients are unaffected (debt #13).
     */
    private List<PlanningDemand> resolveDemands(MrpRunCreateRequest request,
                                                Company company,
                                                Plant plant,
                                                Warehouse warehouse) {
        if (request.demandLineIds() == null || request.demandLineIds().isEmpty()) {
            return planningDemandRepository.findOpenDemandsForRun(
                    company.getCompanyId(),
                    plant.getPlantId(),
                    warehouse == null ? null : warehouse.getWarehouseId(),
                    request.horizonStartDate(),
                    request.horizonEndDate());
        }

        List<UUID> requestedIds = request.demandLineIds().stream().distinct().toList();
        List<PlanningDemand> selected = planningDemandRepository.findSelectedDemandsForRun(requestedIds);
        Map<UUID, PlanningDemand> byId = selected.stream()
                .collect(Collectors.toMap(PlanningDemand::getPlanningDemandId, demand -> demand));
        for (UUID demandId : requestedIds) {
            ensureDemandEligible(byId.get(demandId), demandId, company, plant, warehouse);
        }
        return selected;
    }

    private void ensureDemandEligible(PlanningDemand demand,
                                      UUID demandId,
                                      Company company,
                                      Plant plant,
                                      Warehouse warehouse) {
        // Out-of-scope demands are reported as "not found" rather than "forbidden" (spec §8.2:
        // ENTITY_NOT_FOUND covers "không tìm thấy hoặc khác plant") so a planner cannot probe for
        // the existence of another plant's demand.
        boolean outOfScope = demand == null
                || !demand.getCompany().getCompanyId().equals(company.getCompanyId())
                || !demand.getPlant().getPlantId().equals(plant.getPlantId())
                || (warehouse != null
                    && demand.getWarehouse() != null
                    && !demand.getWarehouse().getWarehouseId().equals(warehouse.getWarehouseId()));
        if (outOfScope) {
            throw ExceptionFactory.notFound(
                    ValidationErrorCode.RESOURCE_NOT_FOUND, "Planning demand", demandId);
        }
        if (demand.getStatus() != PlanningDemandStatus.OPEN) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Planning demand " + demandId + " is " + demand.getStatus() + ", only OPEN demands can be planned");
        }
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
                    .projectedAvailableQuantity(draft.projectedAvailableQuantity())
                    .netRequiredQuantity(draft.netRequiredQuantity())
                    .dueDate(draft.dueDate())
                    .requirementStatus(draft.status())
                    .note(draft.note())
                    .settingSource(draft.settingSource())
                    .warehouseResolutionSource(draft.warehouseResolutionSource())
                    .excludedLotCount(draft.excludedLotCount())
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
                            .outputWarehouse(draft.suggestionType() == SupplySuggestionType.WORK_ORDER
                                    ? requirement.warehouse() : null)
                            .receivingWarehouse(draft.suggestionType() == SupplySuggestionType.PURCHASE_REQUISITION
                                    ? requirement.warehouse() : null)
                            .item(requirement.item())
                            .suggestionType(draft.suggestionType())
                            .suggestedQuantity(requirement.netRequiredQuantity())
                            .neededByDate(requirement.dueDate())
                            .suggestedOrderDate(requirement.suggestedOrderDate())
                            .sourceRoutingCode(draft.sourceRoutingCode())
                            .sourceRoutingVersion(draft.sourceRoutingVersion())
                            .exceptionState(draft.exceptionState())
                            .messageCodes(draft.messages().stream()
                                    .map(Enum::name)
                                    .collect(Collectors.joining(",")))
                            .build();
                })
                .toList());
    }

    // The four Run-header cells of spec §2.4 are counted off the calculation result already in hand,
    // not re-queried: the run row is a cache of numbers this transaction just produced.

    /**
     * Total gross demand the run started from (spec §2.4). Only {@code level == 0} requirements
     * count: every deeper level is <em>derived</em> from those through the BOM, so summing all
     * levels would report the same demand once per BOM level and make a two-level product look
     * twice as demanded as a single-level one.
     */
    private BigDecimal sumGrossDemand(MrpCalculationService.MrpCalculationResult result) {
        return result.requirements().stream()
                .filter(requirement -> requirement.level() == 0)
                .map(MrpCalculationService.RequirementDraft::grossRequiredQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int countShortageLines(MrpCalculationService.MrpCalculationResult result) {
        return (int) result.requirements().stream()
                .filter(requirement -> requirement.netRequiredQuantity().compareTo(BigDecimal.ZERO) > 0)
                .count();
    }

    private int countSuggestions(MrpCalculationService.MrpCalculationResult result,
                                 SupplySuggestionType suggestionType) {
        return (int) result.suggestions().stream()
                .filter(suggestion -> suggestion.suggestionType() == suggestionType)
                .count();
    }

    private int countBlockedProposals(MrpCalculationService.MrpCalculationResult result) {
        return (int) result.suggestions().stream()
                .filter(suggestion -> suggestion.exceptionState() == SupplySuggestionExceptionState.BLOCKED)
                .count();
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
                        RequestContext.capture(attrs.getRequest()), action, "MrpRun",
                        run.getMrpRunId(), run.getCode());
            } else {
                auditLogService.log(
                        RequestContext.capture(attrs.getRequest()), action, description);
            }
        } catch (RuntimeException ignored) {
            // Audit must not change the MRP business outcome.
        }
    }
}
