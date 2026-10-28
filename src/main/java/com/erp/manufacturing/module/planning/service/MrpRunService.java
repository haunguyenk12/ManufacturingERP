package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.RequestContext;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.dto.CommercialPlanningRunRequest;
import com.erp.manufacturing.module.planning.dto.ItemWarehouseMappingRequest;
import com.erp.manufacturing.module.planning.dto.MrpRequirementLineResponse;
import com.erp.manufacturing.module.planning.dto.MrpRunCreateRequest;
import com.erp.manufacturing.module.planning.dto.MrpRunResponse;
import com.erp.manufacturing.module.planning.dto.PendingQcSupplyResponse;
import com.erp.manufacturing.module.planning.dto.SupplySuggestionResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.*;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSource;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MrpRunService {

    /** Shown to the client when the underlying failure was never meant for one to read. */
    static final String GENERIC_FAILURE_MESSAGE = "MRP calculation failed - see server logs";

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
    private final MrpRunStateRecorder runStateRecorder;
    private final SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    private final PlanningSourceVersionCalculator sourceVersionCalculator;
    private final SupplySuggestionConversionRepository conversionRepository;
    private final ItemLookupService itemLookupService;
    private final PurchaseQcLookupService purchaseQcLookupService;
    private final MrpPendingQcSourceRepository pendingQcSourceRepository;

    /** FE R08 / A15: the commercial period is at most 90 days, both ends included. */
    static final long MAX_COMMERCIAL_PERIOD_DAYS = 90;

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
    @Auditable(action = AuditAction.MRP_RUN_CREATED, entityType = "MrpRun", entityIdExpression = "mrpRunId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
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
        // Committed in a transaction of its own (EH-4). Flushing inside this transaction was not
        // enough on two counts: an uncommitted row is invisible to the failure recorder below, and it
        // makes a concurrent duplicate key block on the index rather than fail fast.
        UUID mrpRunId = runStateRecorder.start(run);
        MrpRun persistedRun = mrpRunRepository.findById(mrpRunId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", mrpRunId));

        mrpRunDemandRepository.saveAll(demands.stream()
                .map(demand -> snapshotDemand(persistedRun, demand))
                .toList());

        try {
            MrpCalculationService.MrpCalculationResult result = calculationService.calculate(
                    persistedRun, demands, scopeWarehouseIds);
            persistResult(persistedRun, demands.size(), result, scopeWarehouseIds);
            auditRunOutcome(AuditAction.MRP_RUN_COMPLETED, persistedRun, null);
        } catch (RuntimeException e) {
            String recorded = clientSafeFailureMessage(e);
            log.error("MRP run {} failed: {}", persistedRun.getCode(), e.getMessage(), e);
            MrpRunResponse failed = runStateRecorder.recordFailure(mrpRunId, recorded);
            auditRunOutcome(AuditAction.MRP_RUN_FAILED, persistedRun, recorded);
            return failed;
        }

        return mapper.toResponse(mrpRunRepository.save(persistedRun));
    }

    /**
     * Commercial run (G6b, D6): plans the <em>uncovered</em> quantity of the selected sales order
     * lines — ordered minus QC accepted, held stock and open work order coverage (B172) — so stock
     * already held for an order is not counted twice (A16). The engine, netting and proposals are
     * the standard run's; what differs is the input and the source version recorded at the end.
     *
     * <p>Nothing is written for an unusable selection (rule C9): every line, mapping and demand is
     * checked before the run row exists. {@code Idempotency-Key} behaves exactly as on {@link #run}.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MRP_RUN', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.MRP_RUN_CREATED, entityType = "MrpRun", entityIdExpression = "mrpRunId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()")
    public MrpRunResponse runCommercial(CommercialPlanningRunRequest request, String idempotencyKey) {
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
        validateCommercialPeriod(request);
        if (Boolean.TRUE.equals(request.includeRop())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "includeRop is not available: item-warehouse settings carry no reorder quantity, "
                            + "so a reorder-point lane cannot be computed; send false");
        }
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);
        Map<UUID, Warehouse> itemWarehouses = resolveItemWarehouses(request, company, plant);

        List<UUID> lineIds = distinctLineIds(request.salesOrderLineIds());
        Map<UUID, SalesOrderPlanningSource> sources = salesOrderPlanningSourceService.findSources(lineIds);
        for (UUID lineId : lineIds) {
            ensureSourceEligible(sources.get(lineId), lineId, company, plant);
        }
        Map<UUID, PlanningDemand> demandByLine = planningDemandRepository.findOpenByReference(
                        PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE,
                        lineIds.stream().map(UUID::toString).toList())
                .stream()
                .collect(Collectors.toMap(d -> UUID.fromString(d.getReferenceId()), d -> d, (a, b) -> a));
        for (UUID lineId : lineIds) {
            if (!demandByLine.containsKey(lineId)) {
                throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                        "Sales order line " + lineId + " has no OPEN planning demand");
            }
        }
        // Reloaded with its associations, in the same order the standard run nets demands.
        List<PlanningDemand> demands = planningDemandRepository.findSelectedDemandsForRun(
                demandByLine.values().stream().map(PlanningDemand::getPlanningDemandId).toList());
        Map<UUID, BigDecimal> uncoveredByDemand = new HashMap<>();
        demandByLine.forEach((lineId, demand) ->
                uncoveredByDemand.put(demand.getPlanningDemandId(), sources.get(lineId).uncoveredQuantity()));
        List<MrpCalculationService.TopLevelDemand> topLevels = demands.stream()
                .map(demand -> new MrpCalculationService.TopLevelDemand(
                        demand, uncoveredByDemand.get(demand.getPlanningDemandId()), null))
                .toList();

        MrpRun run = MrpRun.builder()
                .company(company)
                .plant(plant)
                .horizonStartDate(request.horizonStartDate())
                .horizonEndDate(request.horizonEndDate())
                .runKind(MrpRunKind.COMMERCIAL)
                .inputSnapshot(new MrpRunInputSnapshot(
                        lineIds,
                        request.itemWarehouseMappings() == null ? List.of()
                                : request.itemWarehouseMappings().stream()
                                    .map(m -> new MrpRunInputSnapshot.ItemWarehouse(
                                            m.itemId(), m.demandWarehouseId(),
                                            m.outputWarehouseId() == null ? m.demandWarehouseId() : m.outputWarehouseId()))
                                    .toList(),
                        false))
                .idempotencyKey(normalizedKey)
                .payloadHash(normalizedKey == null ? null : idempotency.payloadHash(request))
                .build();
        UUID mrpRunId = runStateRecorder.start(run);
        MrpRun persistedRun = mrpRunRepository.findById(mrpRunId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "MRP run", mrpRunId));
        mrpRunDemandRepository.saveAll(demands.stream()
                .map(demand -> snapshotDemand(persistedRun, demand, uncoveredByDemand.get(demand.getPlanningDemandId())))
                .toList());

        try {
            List<UUID> scopeWarehouseIds = resolveScopeWarehouseIds(plant, null);
            MrpCalculationService.MrpCalculationResult result = calculationService.calculate(
                    persistedRun, topLevels, scopeWarehouseIds, itemWarehouses);
            persistResult(persistedRun, demands.size(), result, scopeWarehouseIds);
            persistedRun.setSourceVersion(sourceVersionCalculator.compute(
                    company.getCompanyId(),
                    lineIds,
                    result.requirements().stream()
                            .map(MrpCalculationService.RequirementDraft::item)
                            .filter(item -> PlanningSourceVersionCalculator.MANUFACTURABLE_TYPES.contains(item.getType()))
                            .map(Item::getItemId)
                            .distinct()
                            .toList()));
            auditRunOutcome(AuditAction.MRP_RUN_COMPLETED, persistedRun, null);
        } catch (RuntimeException e) {
            String recorded = clientSafeFailureMessage(e);
            log.error("MRP run {} failed: {}", persistedRun.getCode(), e.getMessage(), e);
            MrpRunResponse failed = runStateRecorder.recordFailure(mrpRunId, recorded);
            auditRunOutcome(AuditAction.MRP_RUN_FAILED, persistedRun, recorded);
            return failed;
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
        MrpRun run = findRun(runId);
        return mapper.toResponse(run, currentSourceState(run));
    }

    /**
     * Requirement lines with their proposal and converted quantity (G6b). Two batch queries for the
     * page — proposals, then ledger sums — never one per line (C14).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public PageResult<MrpRequirementLineResponse> listRequirements(UUID runId, Pageable pageable) {
        ensureRunExists(runId);
        Page<MrpRequirementLine> page = requirementLineRepository.findByMrpRunMrpRunId(runId, pageable);
        List<UUID> lineIds = page.getContent().stream().map(MrpRequirementLine::getMrpRequirementLineId).toList();
        Map<UUID, SupplySuggestion> suggestions = lineIds.isEmpty() ? Map.of()
                : supplySuggestionRepository.findByRequirementLineIds(lineIds).stream()
                    .collect(Collectors.toMap(s -> s.getRequirementLine().getMrpRequirementLineId(), s -> s, (a, b) -> a));
        Map<UUID, SupplySuggestionConversionRepository.SuggestionQuantityProjection> converted =
                convertedBySuggestion(suggestions.values());
        return PageResult.from(page.map(line -> {
            SupplySuggestion suggestion = suggestions.get(line.getMrpRequirementLineId());
            SupplySuggestionConversionRepository.SuggestionQuantityProjection ledger =
                    suggestion == null ? null : converted.get(suggestion.getSupplySuggestionId());
            return mapper.toResponse(line, suggestion,
                    ledger == null ? BigDecimal.ZERO : ledger.getQuantity(),
                    ledger != null,
                    null);
        }));
    }

    /**
     * CF-4 (B267): the pending purchase-QC sources a run captured. {@code PERM_MRP_READ} on the run is enough — the
     * projection carries no price or supplier contact (FE Q2). A run before V93 simply has none.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public PageResult<PendingQcSupplyResponse> listPendingQcSupplies(UUID runId, UUID requirementLineId,
                                                                     UUID warehouseId, Pageable pageable) {
        ensureRunExists(runId);
        return PageResult.from(pendingQcSourceRepository.search(runId, requirementLineId, warehouseId, pageable)
                .map(mapper::toResponse));
    }

    /** One requirement line with its proposal and every conversion made from it (G6b). */
    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasRunAccess(authentication, 'PERM_MRP_READ', #runId)")
    public MrpRequirementLineResponse getRequirement(UUID runId, UUID requirementLineId) {
        MrpRequirementLine line = requirementLineRepository
                .findByMrpRunMrpRunIdAndMrpRequirementLineId(runId, requirementLineId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Requirement line", requirementLineId));
        SupplySuggestion suggestion = supplySuggestionRepository
                .findByRequirementLineIds(List.of(requirementLineId)).stream().findFirst().orElse(null);
        List<SupplySuggestionConversion> rows =
                conversionRepository.findByRequirementLineIdOrderByCreatedAtAscEntryNoAsc(requirementLineId);
        BigDecimal ledgerSum = rows.stream().filter(row -> !row.isReleased())
                .map(SupplySuggestionConversion::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return mapper.toResponse(line, suggestion, ledgerSum, !rows.isEmpty(),
                rows.stream().map(mapper::toResponse).toList());
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
        return snapshotDemand(run, demand, demand.getRequiredQuantity());
    }

    /** {@code plannedQuantity}: what the run plans for this demand — the uncovered quantity on a commercial run. */
    private MrpRunDemand snapshotDemand(MrpRun run, PlanningDemand demand, BigDecimal plannedQuantity) {
        return MrpRunDemand.builder()
                .mrpRun(run)
                .planningDemand(demand)
                .item(demand.getItem())
                .warehouse(demand.getWarehouse())
                .requiredQuantity(plannedQuantity)
                .dueDate(demand.getDueDate())
                .priority(demand.getPriority())
                .build();
    }

    private Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> persistRequirements(
            MrpRun run,
            List<MrpCalculationService.RequirementDraft> drafts,
            Map<MrpCalculationService.RequirementDraft, List<PurchaseQcLookupService.PendingQcLine>> pendingQc) {
        Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> saved = new IdentityHashMap<>();
        for (MrpCalculationService.RequirementDraft draft : drafts) {
            List<PurchaseQcLookupService.PendingQcLine> sources = pendingQc.get(draft);
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
                    .pendingQcQuantity(sources.stream()
                            .map(PurchaseQcLookupService.PendingQcLine::pendingQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .pendingQcSourceCount(sources.size())
                    .build();
            MrpRequirementLine savedLine = requirementLineRepository.save(line);
            saved.put(draft, savedLine);
            pendingQcSourceRepository.saveAll(sources.stream()
                    .map(source -> MrpPendingQcSource.builder()
                            .mrpRunId(run.getMrpRunId())
                            .requirementLineId(savedLine.getMrpRequirementLineId())
                            .goodsReceiptId(source.goodsReceiptId())
                            .goodsReceiptLineId(source.goodsReceiptLineId())
                            .receiptCode(source.receiptCode())
                            .receiptVersion(source.receiptVersion())
                            .itemId(source.itemId())
                            .warehouseId(source.warehouseId())
                            .uomCode(source.uomCode())
                            .pendingQuantity(source.pendingQuantity())
                            .build())
                    .toList());
        }
        return saved;
    }

    private void persistSuggestions(MrpRun run,
                                    List<MrpCalculationService.SuggestionDraft> suggestions,
                                    Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> savedRequirements) {
        // CF-4 (B268): a BUY proposal whose item has purchase stock awaiting QC carries a warning; it is never blocked
        // and the quantity it proposes does not change (pending stock is shown, not netted).
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
                            .exceptionState(withPendingQcWarning(draft, requirementLine)
                                    && draft.exceptionState() == SupplySuggestionExceptionState.READY
                                    ? SupplySuggestionExceptionState.WARNING : draft.exceptionState())
                            .messageCodes(Stream.concat(draft.messages().stream(),
                                            withPendingQcWarning(draft, requirementLine)
                                                    ? Stream.of(PlanningMessageCode.PENDING_QC_SUPPLY) : Stream.empty())
                                    .map(Enum::name)
                                    .collect(Collectors.joining(",")))
                            .build();
                })
                .toList());
    }

    private static boolean withPendingQcWarning(MrpCalculationService.SuggestionDraft draft, MrpRequirementLine line) {
        return draft.suggestionType() == SupplySuggestionType.PURCHASE_REQUISITION
                && line.getPendingQcQuantity() != null && line.getPendingQcQuantity().signum() > 0;
    }

    /**
     * CF-4 (B267): which goods receipt lines held pending purchase-QC quantity of each requirement's item, in the
     * requirement's warehouse (or the run's scope warehouses when the line has none, the same set its netting used).
     * One query for the whole run (C14). The snapshot is never rewritten by QC decided later.
     */
    private Map<MrpCalculationService.RequirementDraft, List<PurchaseQcLookupService.PendingQcLine>> capturePendingQc(
            List<MrpCalculationService.RequirementDraft> drafts, Collection<UUID> scopeWarehouseIds) {
        Set<UUID> itemIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>(scopeWarehouseIds);
        for (MrpCalculationService.RequirementDraft draft : drafts) {
            itemIds.add(draft.item().getItemId());
            if (draft.warehouse() != null) {
                warehouseIds.add(draft.warehouse().getWarehouseId());
            }
        }
        List<PurchaseQcLookupService.PendingQcLine> pending =
                purchaseQcLookupService.pendingQualityLines(itemIds, warehouseIds);
        Map<MrpCalculationService.RequirementDraft, List<PurchaseQcLookupService.PendingQcLine>> byDraft =
                new IdentityHashMap<>();
        for (MrpCalculationService.RequirementDraft draft : drafts) {
            Collection<UUID> lineWarehouses = draft.warehouse() == null
                    ? scopeWarehouseIds : List.of(draft.warehouse().getWarehouseId());
            byDraft.put(draft, pending.stream()
                    .filter(source -> source.itemId().equals(draft.item().getItemId())
                            && lineWarehouses.contains(source.warehouseId()))
                    .toList());
        }
        return byDraft;
    }

    private void persistResult(MrpRun persistedRun, int demandCount, MrpCalculationService.MrpCalculationResult result,
                               Collection<UUID> scopeWarehouseIds) {
        Map<MrpCalculationService.RequirementDraft, MrpRequirementLine> savedRequirements =
                persistRequirements(persistedRun, result.requirements(),
                        capturePendingQc(result.requirements(), scopeWarehouseIds));
        persistSuggestions(persistedRun, result.suggestions(), savedRequirements);
        persistedRun.complete(
                // Postgres keeps microseconds; a 100 ns Windows clock reading would make the response
                // disagree with every later read of the same run (the G3/G4 lesson, §0.51 #3).
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                demandCount,
                result.requirements().size(),
                result.suggestions().size(),
                sumGrossDemand(result),
                countShortageLines(result),
                countSuggestions(result, SupplySuggestionType.WORK_ORDER),
                countSuggestions(result, SupplySuggestionType.PURCHASE_REQUISITION),
                countBlockedProposals(result));
    }

    private Map<UUID, SupplySuggestionConversionRepository.SuggestionQuantityProjection> convertedBySuggestion(
            Collection<SupplySuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return Map.of();
        }
        return conversionRepository.sumBySuggestionIds(
                        suggestions.stream().map(SupplySuggestion::getSupplySuggestionId).toList())
                .stream()
                .collect(Collectors.toMap(
                        SupplySuggestionConversionRepository.SuggestionQuantityProjection::getSupplySuggestionId,
                        projection -> projection));
    }

    /** {@code CURRENT}/{@code STALE} for a completed commercial run; {@code null} for any other run. */
    private String currentSourceState(MrpRun run) {
        if (!run.isCommercial() || run.getSourceVersion() == null || run.getStatus() != MrpRunStatus.COMPLETED) {
            return null;
        }
        return run.getSourceVersion().equals(sourceVersionCalculator.computeCurrent(run)) ? "CURRENT" : "STALE";
    }

    private void validateCommercialPeriod(CommercialPlanningRunRequest request) {
        if (request.horizonStartDate().isAfter(request.horizonEndDate())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("horizonEndDate", "must not be before horizonStartDate"));
        }
        long days = ChronoUnit.DAYS.between(request.horizonStartDate(), request.horizonEndDate()) + 1;
        if (days > MAX_COMMERCIAL_PERIOD_DAYS) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("horizonEndDate", "planning period must be at most "
                            + MAX_COMMERCIAL_PERIOD_DAYS + " days, both ends included"));
        }
    }

    private List<UUID> distinctLineIds(List<UUID> salesOrderLineIds) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < salesOrderLineIds.size(); i++) {
            if (!seen.add(salesOrderLineIds.get(i))) {
                errors.put("salesOrderLineIds[" + i + "]", "is listed more than once");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        return List.copyOf(salesOrderLineIds);
    }

    /**
     * Run-only warehouse choice per item (FE Q9/A21): ACTIVE, in this plant, item of this company.
     * Master data is not touched. The output warehouse must be the netting warehouse (FE Q16).
     */
    private Map<UUID, Warehouse> resolveItemWarehouses(CommercialPlanningRunRequest request, Company company, Plant plant) {
        List<ItemWarehouseMappingRequest> mappings = request.itemWarehouseMappings() == null
                ? List.of() : request.itemWarehouseMappings();
        Map<String, String> errors = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < mappings.size(); i++) {
            ItemWarehouseMappingRequest mapping = mappings.get(i);
            if (!seen.add(mapping.itemId())) {
                errors.put("itemWarehouseMappings[" + i + "].itemId", "is mapped more than once");
            }
            if (mapping.outputWarehouseId() != null && !mapping.outputWarehouseId().equals(mapping.demandWarehouseId())) {
                errors.put("itemWarehouseMappings[" + i + "].outputWarehouseId",
                        "must equal demandWarehouseId: planned transfers between warehouses are not supported");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        Map<UUID, Warehouse> result = new HashMap<>();
        for (ItemWarehouseMappingRequest mapping : mappings) {
            Item item = itemLookupService.getActiveItem(mapping.itemId());
            if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                        "Item " + mapping.itemId() + " does not belong to the selected company");
            }
            result.put(item.getItemId(),
                    organizationLookupService.getActiveWarehouseInPlant(mapping.demandWarehouseId(), plant.getPlantId()));
        }
        return result;
    }

    /** Out of scope reads as not found, like the standard run's demand selection (B60). */
    private void ensureSourceEligible(SalesOrderPlanningSource source, UUID lineId, Company company, Plant plant) {
        if (source == null
                || !source.companyId().equals(company.getCompanyId())
                || !source.plantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order line", lineId);
        }
        if (!source.planningEligible()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Sales order " + source.salesOrderNo() + " is " + source.orderStatus() + " and cannot be planned");
        }
        if (source.uncoveredQuantity().signum() <= 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Sales order line " + lineId + " has nothing left to plan (uncovered quantity is 0)");
        }
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

    /**
     * What {@code MrpRunResponse.errorMessage} is allowed to say.
     *
     * <p>An {@link AppException} was raised on purpose by this system and its message was written for
     * whoever made the request, so it is passed through — that is how a planner learns their run hit
     * {@code AMBIGUOUS_WAREHOUSE_POLICY} rather than just "it failed". Anything else is an
     * unanticipated failure whose message can be a driver or Hibernate string; those went straight
     * onto the response before EH-4, the one place in this codebase that leaked internals to a client.
     * The full exception is logged either way, so nothing is lost for diagnosis.
     */
    private String clientSafeFailureMessage(RuntimeException e) {
        return e instanceof AppException ? e.getMessage() : GENERIC_FAILURE_MESSAGE;
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
