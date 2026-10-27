package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRun;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.CostTotals;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Checklist;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ChecklistCost;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ClosedInfo;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostCheck;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostSnapshot;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostView;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ListRow;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Overview;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Readiness;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunSummary;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Section;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.SourceRef;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.VarianceView;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.ActorRef;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRunRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * G11b: production cost of work orders — list (C3), detail by section (C4), checklist (C6), preview runs (C7–C9).
 *
 * <p>Every read evaluates the live sources through {@link ProductionCostSourceReader} and
 * {@link ProductionCostCalculator}; nothing is cached, so a number can never lag behind the ledgers it comes from. A
 * preview run stores that evaluation with its source version and changes nothing else — not the work order version,
 * not stock (FE R16, A19).
 */
@Service
@RequiredArgsConstructor
public class ProductionCostService {

    static final Set<WorkOrderStatus> COST_STATUSES = EnumSet.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED);
    static final Set<WorkOrderStatus> CLOSE_STATUSES = EnumSet.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED);
    static final Set<WorkOrderStatus> CALCULABLE = EnumSet.of(WorkOrderStatus.RELEASED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.COMPLETED);

    private final WorkOrderRepository workOrderRepository;
    private final ProductionCostRunRepository runRepository;
    private final ProductionCostSourceReader sourceReader;
    private final WorkOrderIdempotency idempotencyRecords;
    private final PermissionGuard permissionGuard;
    private final OrganizationLookupService organizationLookupService;
    private final UserLookupService userLookupService;
    private final ObjectMapper objectMapper;

    /** The live cost of one work order, its latest run and readiness. */
    public record Evaluation(WorkOrder workOrder, CostSnapshot snapshot, String sourceVersion, ProductionCostRun latestRun,
                             ProductionCostRun finalRun, Readiness readiness) {
    }

    // ------------------------------------------------------------------ C3

    @Transactional(readOnly = true)
    @PreAuthorize("#mode == 'close' ? ("
            + "@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_VARIANCE_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_COST_CALCULATE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_CLOSE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_WIP_READ', 'PLANT', #plantId))"
            + " : (@permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_VARIANCE_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_COST_CALCULATE', 'PLANT', #plantId))")
    public PageResult<ListRow> list(UUID plantId, String mode, String search, Readiness readiness, LocalDate fromDate,
                                    LocalDate toDate, Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("toDate", "must not be before fromDate"));
        }
        if (search != null && search.length() > 200) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("search", "must be at most 200 characters"));
        }
        ZoneId zone = (fromDate == null && toDate == null) ? null
                : ZoneId.of(organizationLookupService.getActivePlant(plantId).getTimezone());
        Set<WorkOrderStatus> statuses = "close".equals(mode) ? CLOSE_STATUSES : COST_STATUSES;
        String term = StringUtils.hasText(search) ? search.trim() : null;
        boolean money = hasCostView(plantId);
        if (readiness == null) {
            Page<WorkOrder> page = workOrderRepository.searchWip(plantId, statuses, null, term,
                    fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant(),
                    toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant(), pageable);
            return PageResult.from(page.map(workOrder -> toRow(evaluate(workOrder), money)));
        }
        // Readiness is derived from the live sources, so it can only be filtered after evaluating every candidate
        // (FE R01: filter before paging). Bounded by the plant's open cost work orders.
        List<ListRow> rows = workOrderRepository.searchWip(plantId, statuses, null, term,
                        fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant(),
                        toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant(),
                        PageRequest.of(0, Integer.MAX_VALUE, pageable.getSort())).getContent().stream()
                .map(this::evaluate)
                .filter(evaluation -> evaluation.readiness() == readiness)
                .map(evaluation -> toRow(evaluation, money))
                .toList();
        return page(rows, pageable.getPageNumber(), pageable.getPageSize());
    }

    // ------------------------------------------------------------------ C4

    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.COST_VIEW_ON_WORK_ORDER)
    public CostView get(UUID workOrderId, Section section, int page, int size) {
        Evaluation evaluation = evaluate(findWorkOrder(workOrderId));
        Section shown = section == null ? Section.OVERVIEW : section;
        return view(evaluation, shown, data(evaluation.snapshot(), shown, page, size), evaluation.sourceVersion());
    }

    // ------------------------------------------------------------------ C6

    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.CHECKLIST_ON_WORK_ORDER)
    public Checklist checklist(UUID workOrderId) {
        Evaluation evaluation = evaluate(findWorkOrder(workOrderId));
        WorkOrder workOrder = evaluation.workOrder();
        boolean money = hasCostView(workOrder.getPlant().getPlantId());
        CostSnapshot snapshot = evaluation.snapshot();
        ProductionCostRun latest = evaluation.latestRun();
        ChecklistCost cost = money ? new ChecklistCost(snapshot.planned(), snapshot.actual(), snapshot.variance().total(),
                latest == null ? null : latest.getRunId(), latest == null ? null : latest.getRunVersion()) : null;
        return new Checklist(workOrder.getWorkOrderId(), workOrder.getWorkOrderNo(), workOrder.getStatus(),
                workOrder.getVersion(), evaluation.readiness(), money, allowedActions(evaluation),
                snapshot.quantities(), snapshot.checks(), money ? evaluation.sourceVersion() : null, cost,
                closed(evaluation.finalRun()));
    }

    // ------------------------------------------------------------------ C7

    /**
     * Saves a preview run of the current sources. {@code expectedVersion} is the work order version and
     * {@code expectedSourceVersion} the source version the user was looking at: if either moved, the user sees stale
     * numbers and is told so (FE E11) rather than saving a calculation they never saw.
     */
    @Transactional
    @PreAuthorize(CostAccess.CALCULATE_ON_WORK_ORDER)
    @Auditable(action = AuditAction.PRODUCTION_COST_RUN_CREATED, entityType = "WorkOrder",
            entityIdExpression = "#workOrderId.toString()")
    public RunCommandResult createRun(UUID workOrderId, RunRequest request, String rawKey) {
        String key = idempotencyRecords.requiredKey(rawKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(workOrderId, request);
        var replayed = idempotencyRecords.replay(key, WorkOrderCommand.COST_RUN_CREATE, fingerprint);
        if (replayed.isPresent()) {
            ProductionCostRun run = runRepository.findById(replayed.get()).orElseThrow();
            return new RunCommandResult(workOrderId, run.getRunId(), run.getRunVersion(), run.getWorkOrderVersion());
        }
        WorkOrder workOrder = findWorkOrder(workOrderId);
        if (!Objects.equals(workOrder.getVersion(), request.expectedVersion())) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.CONCURRENT_MODIFICATION, Map.of("expectedVersion",
                    "work order version is " + workOrder.getVersion() + ", not " + request.expectedVersion()));
        }
        if (!CALCULABLE.contains(workOrder.getStatus())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Production cost is calculated for released, in-progress or completed work orders only");
        }
        ProductionCostSourceReader.Sources sources = sourceReader.read(workOrder);
        ensureFresh(sources.sourceVersion(), request.expectedSourceVersion());
        CostSnapshot snapshot = ProductionCostCalculator.calculate(sources.inputs());
        ProductionCostRun run = runRepository.saveAndFlush(ProductionCostRun.builder()
                .runNo("CST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .workOrderId(workOrderId)
                .plantId(workOrder.getPlant().getPlantId())
                .sourceVersion(sources.sourceVersion())
                .workOrderVersion(workOrder.getVersion())
                .plannedTotal(snapshot.planned().total())
                .actualTotal(snapshot.actual().total())
                .snapshot(write(snapshot))
                .build());
        idempotencyRecords.record(key, WorkOrderCommand.COST_RUN_CREATE, run.getRunId(), fingerprint);
        return new RunCommandResult(workOrderId, run.getRunId(), run.getRunVersion(), workOrder.getVersion());
    }

    // ------------------------------------------------------------------ C8 / C9

    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.COST_VIEW_ON_WORK_ORDER)
    public PageResult<RunSummary> runs(UUID workOrderId, Pageable pageable) {
        findWorkOrder(workOrderId);
        Page<ProductionCostRun> page = runRepository.findByWorkOrder(workOrderId, pageable);
        Map<UUID, String> usernames = usernames(page.getContent().stream()
                .flatMap(run -> Stream.of(run.getCreatedBy(), run.getFinalizedBy())));
        return PageResult.from(page.map(run -> summary(run, usernames)));
    }

    /** A run reads back its own snapshot — never re-calculated from today's sources (FE R26). */
    @Transactional(readOnly = true)
    @PreAuthorize(CostAccess.COST_VIEW_ON_WORK_ORDER)
    public CostView run(UUID workOrderId, UUID runId, Section section, int page, int size) {
        WorkOrder workOrder = findWorkOrder(workOrderId);
        ProductionCostRun run = runRepository.findByRunIdAndWorkOrderId(runId, workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Cost run", runId));
        Evaluation evaluation = evaluate(workOrder);
        CostSnapshot snapshot = read(run.getSnapshot());
        Section shown = section == null ? Section.OVERVIEW : section;
        Evaluation ofRun = new Evaluation(workOrder, snapshot, run.getSourceVersion(), evaluation.latestRun(),
                evaluation.finalRun(), evaluation.readiness());
        return view(ofRun, shown, data(snapshot, shown, page, size), run.getSourceVersion());
    }

    // ------------------------------------------------------------------ evaluation

    /** Live cost + readiness of a work order (FE §3.6). Package-visible for the finalize command. */
    Evaluation evaluate(WorkOrder workOrder) {
        ProductionCostSourceReader.Sources sources = sourceReader.read(workOrder);
        CostSnapshot base = ProductionCostCalculator.calculate(sources.inputs());
        ProductionCostRun latest = latestRun(workOrder.getWorkOrderId());
        ProductionCostRun finalRun = latest != null && latest.isFinalized() ? latest
                : runRepository.findByWorkOrderIdAndState(workOrder.getWorkOrderId(), ProductionCostRun.FINALIZED).orElse(null);
        boolean previewOk = latest != null && !latest.isFinalized()
                && latest.getSourceVersion().equals(sources.sourceVersion());
        List<CostCheck> checks = new ArrayList<>(base.checks());
        checks.add(previewOk ? new CostCheck(ProductionCostCalculator.CHECK_PREVIEW, true, List.of(), null)
                : new CostCheck(ProductionCostCalculator.CHECK_PREVIEW, false,
                List.of(latest == null || latest.isFinalized() ? "NO_PREVIEW" : "PREVIEW_STALE"),
                latest == null ? null : new SourceRef("COST_RUN", latest.getRunId(), latest.getRunNo())));
        CostSnapshot snapshot = withChecks(base, checks);
        Readiness readiness;
        if (workOrder.getStatus() == WorkOrderStatus.CLOSED || finalRun != null) {
            readiness = Readiness.CLOSED;
        } else if (latest != null && !previewOk) {
            readiness = Readiness.STALE;
        } else if (checks.stream().allMatch(CostCheck::ok)) {
            readiness = Readiness.READY;
        } else if (snapshot.planned().total() == null || snapshot.actual().total() == null) {
            readiness = Readiness.INCOMPLETE;
        } else {
            readiness = Readiness.BLOCKED;
        }
        return new Evaluation(workOrder, snapshot, sources.sourceVersion(), latest, finalRun, readiness);
    }

    static CostSnapshot withChecks(CostSnapshot s, List<CostCheck> checks) {
        return new CostSnapshot(s.quantities(), s.currency(), s.planned(), s.actual(), s.variance(), s.variancePercent(),
                s.plannedMinutes(), s.actualMinutes(), s.unitCost(), s.loss(), s.remainingValue(), s.issuedValue(),
                s.unknownReasons(), s.materials(), s.operations(), s.allocations(), List.copyOf(checks));
    }

    static void ensureFresh(String current, String expected) {
        if (!current.equals(expected)) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.COST_SOURCE_STALE, Map.of("expectedSourceVersion",
                    "the cost sources changed since this version was read; calculate again"));
        }
    }

    WorkOrder findWorkOrder(UUID workOrderId) {
        return workOrderRepository.findWithDetailsByWorkOrderId(workOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work order", workOrderId));
    }

    String write(CostSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise a cost snapshot", e);
        }
    }

    CostSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, CostSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read a cost snapshot", e);
        }
    }

    private ProductionCostRun latestRun(UUID workOrderId) {
        return runRepository.findByWorkOrder(workOrderId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ shaping

    private CostView view(Evaluation evaluation, Section section, Object data, String sourceVersion) {
        WorkOrder workOrder = evaluation.workOrder();
        return new CostView(workOrder.getWorkOrderId(), workOrder.getWorkOrderNo(), workOrder.getCompany().getCompanyId(),
                workOrder.getPlant().getPlantId(), workOrder.getProductItem().getItemId(),
                workOrder.getProductItem().getCode(), workOrder.getProductItem().getName(),
                workOrder.getProductItem().getUnit(), workOrder.getStatus(), workOrder.getVersion(),
                workOrder.getCreatedAt(), workOrder.getPlant().getTimezone(), evaluation.readiness(), true,
                sourceVersion, allowedActions(evaluation), section, data, closed(evaluation.finalRun()));
    }

    static Object data(CostSnapshot s, Section section, int page, int size) {
        return switch (section) {
            case OVERVIEW -> overview(s);
            case MATERIALS -> page(s.materials(), page, size);
            case OPERATIONS -> page(s.operations(), page, size);
            case VARIANCE -> new VarianceView(s.planned(), s.actual(), s.variance(), s.variancePercent(),
                    s.quantities().plannedQuantity(), s.quantities().finalGoodQuantity(), s.plannedMinutes(),
                    s.actualMinutes());
            case ALLOCATION -> page(s.allocations(), page, size);
        };
    }

    static Overview overview(CostSnapshot s) {
        var q = s.quantities();
        return new Overview(q.plannedQuantity(), q.finalGoodQuantity(), q.productionScrapQuantity(),
                q.historicalReworkQuantity(), q.shortfallQuantity(), q.approvedReceiptQuantity(),
                q.qcAcceptedQuantity(), q.qcRejectedQuantity(), q.qcPendingQuantity(), s.currency(), s.planned(),
                s.actual(), s.variance().total(), s.variancePercent(), s.unitCost(), s.loss(), s.remainingValue(),
                s.issuedValue(), s.unknownReasons());
    }

    static <T> PageResult<T> page(List<T> all, int page, int size) {
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return PageResult.from(new PageImpl<>(all.subList(from, to), PageRequest.of(safePage, safeSize), all.size()));
    }

    private ListRow toRow(Evaluation evaluation, boolean money) {
        WorkOrder workOrder = evaluation.workOrder();
        CostSnapshot s = evaluation.snapshot();
        CostTotals planned = money ? s.planned() : null;
        CostTotals actual = money ? s.actual() : null;
        return new ListRow(workOrder.getWorkOrderId(), workOrder.getWorkOrderNo(), workOrder.getStatus(),
                workOrder.getProductItem().getItemId(), workOrder.getProductItem().getCode(),
                workOrder.getProductItem().getName(), workOrder.getProductItem().getUnit(),
                workOrder.getPlannedQuantity(), workOrder.getActualGoodQuantity(), workOrder.getCreatedAt(),
                workOrder.getVersion(), evaluation.readiness(), money, planned, actual,
                money ? s.variance().total() : null);
    }

    /** What the current user may do next (FE §3.3); the server re-checks every command anyway. */
    List<String> allowedActions(Evaluation evaluation) {
        WorkOrder workOrder = evaluation.workOrder();
        UUID plantId = workOrder.getPlant().getPlantId();
        List<String> actions = new ArrayList<>();
        if (CALCULABLE.contains(workOrder.getStatus()) && has("PERM_PRODUCTION_COST_CALCULATE", plantId)) {
            actions.add("CALCULATE");
        }
        if (evaluation.readiness() == Readiness.READY && has("PERM_WORK_ORDER_CLOSE", plantId) && hasCostView(plantId)) {
            actions.add("FINALIZE_CLOSE");
        }
        return actions;
    }

    private ClosedInfo closed(ProductionCostRun finalRun) {
        if (finalRun == null) {
            return null;
        }
        UUID by = finalRun.getFinalizedBy();
        return new ClosedInfo(finalRun.getFinalizedAt(), actor(by, by == null ? Map.of() : usernames(Stream.of(by))),
                finalRun.getCloseReason(), finalRun.getRunId());
    }

    private RunSummary summary(ProductionCostRun run, Map<UUID, String> usernames) {
        return new RunSummary(run.getRunId(), run.getRunNo(), run.getRunVersion(), run.getState(), run.getSourceVersion(),
                run.getWorkOrderVersion(), run.getPlannedTotal(), run.getActualTotal(),
                actor(run.getCreatedBy(), usernames), run.getCreatedAt(), run.getFinalizedAt(),
                actor(run.getFinalizedBy(), usernames), run.getCloseReason());
    }

    static ActorRef actor(UUID id, Map<UUID, String> usernames) {
        return id == null ? null : new ActorRef(id, usernames.get(id));
    }

    Map<UUID, String> usernames(Stream<UUID> ids) {
        List<UUID> distinct = ids.filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : userLookupService.findUsernames(distinct);
    }

    boolean hasCostView(UUID plantId) {
        return CostAccess.COST_VIEW.stream().anyMatch(code -> has(code, plantId));
    }

    private boolean has(String permission, UUID plantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && permissionGuard.hasResourceAccess(authentication, permission, "PLANT", plantId);
    }
}
