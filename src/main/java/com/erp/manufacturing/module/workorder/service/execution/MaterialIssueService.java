package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.StockMovement;
import com.erp.manufacturing.module.inventory.service.InventoryIssueCommand;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementResult;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.*;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueFlatRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueLineRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssuePostRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueDetailResponse;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialIssueVersionedDecisionRequest;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.WorkOrderPermissionGuard;
import com.erp.manufacturing.module.workorder.service.WorkOrderVersions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MaterialIssueService {

    private static final String OVERRIDE_PERMISSION = "PERM_MATERIAL_ISSUE_OVERRIDE";

    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final MaterialReservationService reservationService;
    private final InventoryMovementService movementService;
    private final WipTransactionService wipTransactionService;
    private final WorkOrderPermissionGuard workOrderPermissionGuard;
    private final WorkOrderExecutionSupport support;
    private final IdempotencySupport idempotency;
    private final ManufacturingExecutionMapper mapper;
    private final TraceIdProvider traceIdProvider;
    private final UserLookupService userLookupService;
    private final WorkOrderCostAccumulatorService costAccumulatorService;
    private final InventoryLotLookupService inventoryLotLookupService;
    private final WorkOrderIdempotency idempotencyRecords;

    @Autowired(required = false)
    private ProductionExecutionRepository productionExecutionRepository;

    @Autowired(required = false)
    private SecurityAuditorAware auditorAware;

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_POSTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse post(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey, true);
    }

    /**
     * Flat form of {@link #post}: the component line comes from the reservation, so the caller only
     * needs the reservation it is consuming (spec §4.1). Over-issue is impossible here by
     * construction — a reservation can never exceed the component requirement.
     *
     * <p>The reservation is resolved <b>without</b> the {@code ACTIVE} check on purpose (debt #24,
     * fixed in D9/D10). It is read here only to translate the flat request into the multi-line shape;
     * the status is enforced by {@link #postNew}, which every document-creating path goes through.
     * Checking it here meant a retry with the same {@code Idempotency-Key} hit
     * {@code STATE_CONFLICT} — the first call had consumed the reservation — instead of replaying the
     * original document, breaking rule {@code R9} at exactly the endpoint the shop floor retries.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #request.workOrderId())")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_POSTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse postFlat(MaterialIssueFlatRequest request, String idempotencyKey) {
        MaterialReservation reservation = reservationService.findReservationForWorkOrder(
                request.workOrderId(), request.reservationId());
        return postInternal(request.workOrderId(), new MaterialIssuePostRequest(null, List.of(
                new MaterialIssueLineRequest(
                        reservation.getComponentLine().getComponentLineId(),
                        request.reservationId(),
                        reservation.getWarehouse().getWarehouseId(),
                        null,
                        null,
                        null,
                        request.quantity(),
                        request.reason(),
                        null))), idempotencyKey);
    }

    /** The single-line paths ({@code component-issues}, flat {@code POST /material-issues}): no version (G8b). */
    @Transactional
    public MaterialIssueResponse postInternal(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey) {
        return postInternal(workOrderId, request, idempotencyKey, false);
    }

    private MaterialIssueResponse postInternal(UUID workOrderId, MaterialIssuePostRequest request, String idempotencyKey,
                                               boolean versionRequired) {
        String normalizedKey = idempotency.normalizeKey(idempotencyKey);
        return materialIssueRepository.findWithLinesByIdempotencyKey(normalizedKey)
                .map(existing -> {
                    idempotency.ensureSamePayload(existing.getPayloadHash(), request);
                    return toResponse(existing);
                })
                .orElseGet(() -> postNew(workOrderId, request, normalizedKey, versionRequired));
    }

    /**
     * One issue with what a manager needs to decide on it (G8b, FE §3.3): per component, the requirement,
     * what has been issued so far (gross — returns never reduce it, FE Q11), what this document asks for,
     * how far that goes over the BOM, and the stock its sources hold right now. The availability is a
     * snapshot — approval checks the stock again inside its own transaction.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasMaterialIssueAccess(authentication, "
            + "{'PERM_MATERIAL_ISSUE_MANAGE', 'PERM_MATERIAL_ISSUE_APPROVE', 'PERM_MATERIAL_ISSUE_OVERRIDE'}, #issueId)")
    public MaterialIssueDetailResponse getDetail(UUID issueId) {
        MaterialIssue issue = materialIssueRepository.findWithLinesByIssueId(issueId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material issue", issueId));
        Instant asOf = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Map<UUID, List<MaterialIssueLine>> byComponent = new java.util.LinkedHashMap<>();
        issue.getLines().stream()
                .sorted(java.util.Comparator.comparing(line -> line.getComponentLine().getLineNo()))
                .forEach(line -> byComponent.computeIfAbsent(line.getComponentLine().getComponentLineId(),
                        id -> new java.util.ArrayList<>()).add(line));
        List<MaterialIssueDetailResponse.DecisionContext> context = new java.util.ArrayList<>();
        for (List<MaterialIssueLine> lines : byComponent.values()) {
            WorkOrderComponentLine component = lines.get(0).getComponentLine();
            BigDecimal batch = lines.stream().map(MaterialIssueLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal extra = issue.isPendingApproval()
                    ? component.getIssuedQuantity().add(batch).subtract(component.getRequiredQuantity()).max(BigDecimal.ZERO)
                    : null;
            context.add(new MaterialIssueDetailResponse.DecisionContext(
                    component.getComponentLineId(), component.getComponentItem().getItemId(),
                    component.getComponentItem().getCode(), component.getComponentItem().getUnit(),
                    component.getRequiredQuantity(), component.getIssuedQuantity(), batch, extra,
                    currentAvailability(lines), asOf));
        }
        return new MaterialIssueDetailResponse(toResponse(issue), context);
    }

    /**
     * What the sources named by these lines hold now: an active reservation's unissued part, or the free
     * quantity of the balance (item, warehouse, lot). Each source counted once. A lot named only by code is
     * not resolved here — it counts as zero rather than as a guess.
     */
    private BigDecimal currentAvailability(List<MaterialIssueLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MaterialIssueLine line : lines) {
            MaterialReservation reservation = line.getReservation();
            if (reservation != null) {
                if (seen.add("R" + reservation.getReservationId()) && reservation.getStatus() == MaterialReservationStatus.ACTIVE) {
                    total = total.add(reservation.remainingQuantity());
                }
                continue;
            }
            UUID lotId = line.getLot() != null ? line.getLot().getLotId() : line.getRequestedLotId();
            if (lotId == null && line.getComponentLine().getComponentItem().isLotTracked()) {
                continue;
            }
            if (!seen.add("B" + line.getWarehouse().getWarehouseId() + ":" + lotId)) {
                continue;
            }
            total = total.add(freeQuantity(line.getComponentLine(), line.getWarehouse(), lotId));
        }
        return total;
    }

    private BigDecimal freeQuantity(WorkOrderComponentLine component, Warehouse warehouse, UUID lotId) {
        try {
            StockBalance balance = support.findStockBalance(component.getComponentItem(), warehouse, lotId);
            if (balance.getLot() != null && !balance.getLot().canIssue()) {
                return BigDecimal.ZERO;
            }
            return balance.availableQuantity().max(BigDecimal.ZERO);
        } catch (com.erp.manufacturing.common.exception.AppException noBalance) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * One work order's issue history.
     *
     * @param status optional filter; {@code PENDING_APPROVAL} is this work order's Over-BOM approval
     *        queue. Deliberately a single method rather than a no-status convenience overload
     *        delegating to this one: a service method calling its own sibling goes through
     *        {@code this}, not the Spring proxy, so the callee's {@code @PreAuthorize} never runs
     *        (the trap recorded in CLAUDE.md §0.19). The controller passes {@code null} instead.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workOrderPermissionGuard.hasAnyWorkOrderAccess(authentication, "
            + "{'PERM_MATERIAL_ISSUE_MANAGE', 'PERM_MATERIAL_ISSUE_APPROVE', 'PERM_MATERIAL_ISSUE_OVERRIDE'}, #workOrderId)")
    public PageResult<MaterialIssueResponse> list(UUID workOrderId, MaterialIssueStatus status, Pageable pageable) {
        support.findWorkOrder(workOrderId);
        return toPage(materialIssueRepository.findByWorkOrder(workOrderId, status, pageable));
    }

    /**
     * Flat plant-scoped history (spec §4.1). {@code workOrderId} narrows it further and is optional.
     *
     * <p>Authorises on the plant rather than on one work order — there is no single work order id
     * here to authorise against. Reuses {@code PERM_MATERIAL_ISSUE_MANAGE} for the same reason
     * {@link ProductionReceiptService#listByPlant} reuses its own MANAGE permission: {@code V17}
     * seeds one permission per document type covering post and read alike.
     *
     * @param status optional filter; {@code PENDING_APPROVAL} is the plant-wide Over-BOM approval
     *        queue a manager works from.
     *
     * <p>G8b: readable with {@code MANAGE}, {@code APPROVE} or {@code OVERRIDE} (FE §1.4 — the approval
     * queue must open for a manager who never posts issues); {@code search} matches the issue code or the
     * work order number; {@code fromDate}/{@code toDate} are plant-local days on {@code requestedAt}.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_ISSUE_APPROVE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_MATERIAL_ISSUE_OVERRIDE', 'PLANT', #plantId)")
    public PageResult<MaterialIssueResponse> listByPlant(UUID plantId, UUID workOrderId, MaterialIssueStatus status,
                                                         String search, java.time.LocalDate fromDate,
                                                         java.time.LocalDate toDate, Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("toDate", "must not be before fromDate"));
        }
        java.time.ZoneId zone = (fromDate == null && toDate == null) ? null : support.plantZone(plantId);
        Instant requestedFrom = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant requestedTo = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return toPage(materialIssueRepository.findByPlant(plantId, workOrderId, status, support.trimToNull(search),
                requestedFrom, requestedTo, pageable));
    }

    /**
     * Loads the lines, author names and lot codes of a page of issues in one batch query each
     * (rule C14).
     */
    private PageResult<MaterialIssueResponse> toPage(Page<MaterialIssue> page) {
        List<UUID> issueIds = page.getContent().stream()
                .map(MaterialIssue::getIssueId)
                .toList();
        Map<UUID, List<MaterialIssueLine>> linesByIssue = issueIds.isEmpty()
                ? Map.of()
                : materialIssueLineRepository.findByIssueIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(line -> line.getIssue().getIssueId()));
        Map<UUID, String> usernames = usernamesOf(page.getContent());
        Map<UUID, String> lotCodes = lotCodesOf(linesByIssue.values().stream()
                .flatMap(List::stream)
                .toList());
        return PageResult.from(page.map(
                issue -> mapper.toResponse(issue, linesByIssue, usernames, lotCodes)));
    }

    /** Resolves the author username and the lot codes of a single issue document in one query each. */
    private MaterialIssueResponse toResponse(MaterialIssue issue) {
        return mapper.toResponse(issue, usernamesOf(List.of(issue)), lotCodesOf(issue.getLines()));
    }

    /** One query for the whole page (rule C14) — never one lookup per row. */
    private Map<UUID, String> usernamesOf(List<MaterialIssue> issues) {
        return userLookupService.findUsernames(issues.stream()
                .map(MaterialIssue::getCreatedBy)
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * Lot codes for lines that named a lot by id but are not linked to one yet — a PENDING_APPROVAL
     * line only gets its {@code lot} when approval posts the movement. Lines that already carry a
     * lot, or that named it by code, need nothing looked up, so a page of ordinary POSTED history
     * asks for an empty set and never reaches the database (rule C14).
     */
    private Map<UUID, String> lotCodesOf(List<MaterialIssueLine> lines) {
        return inventoryLotLookupService.findLotCodes(lines.stream()
                .filter(line -> line.getLot() == null && line.getRequestedLotCode() == null)
                .map(MaterialIssueLine::getRequestedLotId)
                .filter(Objects::nonNull)
                .toList());
    }

    private MaterialIssueResponse postNew(UUID workOrderId,
                                          MaterialIssuePostRequest request,
                                          String normalizedKey,
                                          boolean versionRequired) {
        WorkOrder workOrder = support.findWorkOrder(workOrderId);
        if (versionRequired) {
            WorkOrderVersions.ensure(workOrder, request.expectedWorkOrderVersion(), "expectedWorkOrderVersion");
        }
        support.ensureExecutable(workOrder);
        validateLineRules(workOrder, request);
        String overLine = firstOverBomLine(workOrder, request);
        if (overLine != null) {
            if (Boolean.FALSE.equals(request.supplemental())) {
                // FE §1.4 step 5: a normal issue never goes over the BOM — the user switches to a supplemental
                // request with a reason instead of the server quietly turning it into one.
                throw ExceptionFactory.withErrors(BusinessErrorCode.OVER_BOM_APPROVAL_REQUIRED, Map.of(overLine,
                        "goes over the BOM requirement; send it as a supplemental issue with a reason"));
            }
            return requestOverBomIssue(workOrder, request, normalizedKey);
        }
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.POSTED)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .supplemental(Boolean.TRUE.equals(request.supplemental()))
                .requestedAt(now)
                .postedAt(now)
                .build();
        // Change the work order once, before anything can flush it: a posted issue starts production. Touching it
        // again after the issue's flush would move the version on by two (see WorkOrderExecutionSupport.flushTouched).
        workOrder.markInProgress();
        workOrder.touch();

        BigDecimal totalIssued = BigDecimal.ZERO;
        int index = 0;
        for (MaterialIssueLineRequest lineRequest : request.lines()) {
            index++;
            WorkOrderComponentLine componentLine = support.findComponentLine(workOrder, lineRequest.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Issue quantity");
            boolean overIssue = isApprovedOverIssue(workOrderId, componentLine, quantity, lineRequest);

            MaterialReservation reservation = null;
            InventoryMovementResult movementResult;
            if (lineRequest.reservationId() != null) {
                reservation = reservationService.findActiveReservationForIssue(workOrderId, lineRequest.reservationId());
                validateReservationForIssue(reservation, componentLine, warehouse, lineRequest, quantity);
                movementResult = movementService.issueReservedManual(issueCommand(
                        componentLine,
                        warehouse,
                        reservation.getLot() != null ? reservation.getLot().getLotId() : null,
                        null,
                        lineRequest.serialId(),
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        idempotency.childKey(normalizedKey, index));
                if (movementResult.created()) {
                    reservation.consume(quantity);
                }
            } else {
                movementResult = movementService.issueManual(issueCommand(
                        componentLine,
                        warehouse,
                        lineRequest.lotId(),
                        lineRequest.lotNumber(),
                        lineRequest.serialId(),
                        quantity,
                        lineRequest.reason(),
                        workOrder),
                        idempotency.childKey(normalizedKey, index));
            }

            StockMovement movement = movementResult.movement();
            if (movementResult.created()) {
                componentLine.addIssuedQuantity(quantity);
                workOrder.markInProgress();
                costAccumulatorService.accumulateMaterialCost(workOrder, componentLine.getComponentItem(), quantity);
            }
            issue.getLines().add(MaterialIssueLine.builder()
                    .issue(issue)
                    .componentLine(componentLine)
                    .reservation(reservation)
                    .item(componentLine.getComponentItem())
                    .warehouse(warehouse)
                    .lot(movement.getLot())
                    .serial(movement.getSerial())
                    .quantity(quantity)
                    .overIssue(overIssue)
                    .overrideReason(support.trimToNull(lineRequest.overrideReason()))
                    .reasonCode(lineRequest.reasonCode())
                    .issueReason(support.trimToNull(lineRequest.reason()))
                    .sourceExecution(resolveSourceExecution(workOrder, lineRequest.sourceExecutionId()))
                    .stockMovement(movement)
                    .build());
            totalIssued = totalIssued.add(quantity);
        }

        MaterialIssue saved = materialIssueRepository.saveAndFlush(issue);
        wipTransactionService.recordMaterialIssued(workOrder, totalIssued, saved.getIssueId());
        // G8 (D5): the work order version moves on with the issue — exactly once — flushed so the response carries it.
        support.flushTouched(workOrder);
        return toResponse(saved);
    }

    /**
     * G8b request rules that need the work order, checked for the whole request before anything is written
     * (C9). Errors name the line: {@code lines[i].field}.
     *
     * <ul>
     *   <li>a supplemental request takes free stock — no {@code reservationId} — and every line carries a
     *       {@code reasonCode} and an {@code overrideReason} (FE §1.4 step 5);</li>
     *   <li>a normal line without a reservation comes from the component's own warehouse when the line has
     *       one (D2); a supplemental line may use any active warehouse of the plant (FE Q8);</li>
     *   <li>{@code sourceExecutionId} only with {@code reasonCode = REWORK} (FE §1.4 step 5, A16);</li>
     *   <li>a reason, when given, is 5–2000 characters (FE Q33).</li>
     * </ul>
     */
    private void validateLineRules(WorkOrder workOrder, MaterialIssuePostRequest request) {
        boolean supplemental = Boolean.TRUE.equals(request.supplemental());
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            MaterialIssueLineRequest line = request.lines().get(i);
            String prefix = "lines[" + i + "].";
            if (supplemental) {
                if (line.reservationId() != null) {
                    errors.put(prefix + "reservationId", "a supplemental line takes free stock, not a reservation");
                }
                if (line.reasonCode() == null) {
                    errors.put(prefix + "reasonCode", "is required on a supplemental line");
                }
                if (!StringUtils.hasText(line.overrideReason())) {
                    errors.put(prefix + "overrideReason", "is required on a supplemental line");
                }
            } else if (line.reservationId() == null) {
                WorkOrderComponentLine component = support.findComponentLine(workOrder, line.componentLineId());
                if (component.getWarehouse() != null && !component.getWarehouse().getWarehouseId().equals(line.warehouseId())) {
                    errors.put(prefix + "warehouseId", "the component is issued from warehouse "
                            + component.getWarehouse().getCode() + " only; use a supplemental issue for another warehouse");
                }
            }
            if (line.sourceExecutionId() != null && line.reasonCode() != MaterialIssueReasonCode.REWORK) {
                errors.put(prefix + "reasonCode", "must be REWORK when sourceExecutionId is given");
            }
            if (StringUtils.hasText(line.overrideReason())) {
                String trimmed = line.overrideReason().trim();
                if (trimmed.length() < ReasonRules.MIN || trimmed.length() > ReasonRules.MAX) {
                    errors.putIfAbsent(prefix + "overrideReason", "must be between " + ReasonRules.MIN + " and "
                            + ReasonRules.MAX + " characters");
                }
            }
        }
        if (!errors.isEmpty()) {
            boolean scope = errors.keySet().stream().allMatch(key -> key.endsWith("warehouseId"));
            throw ExceptionFactory.withErrors(scope ? BusinessErrorCode.RESOURCE_SCOPE_MISMATCH
                    : ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    /**
     * Over-BOM is judged per component on the whole request (FE §1.4 step 6): gross issued so far plus every
     * line of this request for that component, against the requirement. Two lines that each fit but together
     * do not are over — before G8b they were checked one at a time and the second one failed instead.
     *
     * @return the {@code lines[i].quantity} field of the first line of an over component, or {@code null}
     */
    private String firstOverBomLine(WorkOrder workOrder, MaterialIssuePostRequest request) {
        Map<UUID, BigDecimal> requested = requestedByComponent(workOrder, request);
        for (int i = 0; i < request.lines().size(); i++) {
            WorkOrderComponentLine component = support.findComponentLine(workOrder, request.lines().get(i).componentLineId());
            if (requested.get(component.getComponentLineId()).compareTo(component.remainingQuantity()) > 0) {
                return "lines[" + i + "].quantity";
            }
        }
        return null;
    }

    private Map<UUID, BigDecimal> requestedByComponent(WorkOrder workOrder, MaterialIssuePostRequest request) {
        Map<UUID, BigDecimal> requested = new java.util.HashMap<>();
        for (MaterialIssueLineRequest line : request.lines()) {
            WorkOrderComponentLine component = support.findComponentLine(workOrder, line.componentLineId());
            requested.merge(component.getComponentLineId(), support.requirePositive(line.quantity(), "Issue quantity"),
                    BigDecimal::add);
        }
        return requested;
    }

    /**
     * FE Q10: a pending request does not hold stock, but its free-stock sources must hold enough when it is
     * made — a request that could not be posted now is refused instead of waiting for a manager. Lines of the
     * same source are added up first. A lot named only by code is left to the approval check.
     */
    private void ensureSourcesHoldEnough(WorkOrder workOrder, MaterialIssuePostRequest request) {
        Map<String, BigDecimal> requested = new java.util.LinkedHashMap<>();
        Map<String, Integer> firstLine = new java.util.HashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            MaterialIssueLineRequest line = request.lines().get(i);
            WorkOrderComponentLine component = support.findComponentLine(workOrder, line.componentLineId());
            if (line.reservationId() != null || (line.lotId() == null && component.getComponentItem().isLotTracked())) {
                continue;
            }
            // One source is (item, warehouse, lot): two component lines of the same item draw on the same stock.
            String key = component.getComponentItem().getItemId() + ":" + line.warehouseId() + ":" + line.lotId();
            requested.merge(key, line.quantity(), BigDecimal::add);
            firstLine.putIfAbsent(key, i);
        }
        for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
            MaterialIssueLineRequest line = request.lines().get(firstLine.get(entry.getKey()));
            WorkOrderComponentLine component = support.findComponentLine(workOrder, line.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(line.warehouseId(), workOrder.getPlant());
            if (freeQuantity(component, warehouse, line.lotId()).compareTo(entry.getValue()) < 0) {
                throw ExceptionFactory.withErrors(BusinessErrorCode.INSUFFICIENT_STOCK, Map.of(
                        "lines[" + firstLine.get(entry.getKey()) + "].quantity",
                        "the source holds less free stock than requested"));
            }
        }
    }

    /**
     * Gate 1b – issuing beyond the component's remaining BOM requirement is allowed, but only
     * for a user holding {@code PERM_MATERIAL_ISSUE_OVERRIDE} on the work order's plant and only
     * with a justification. The decision is recorded on the issue line itself.
     *
     * @return true when this line issues more than the remaining requirement
     */
    private boolean isApprovedOverIssue(UUID workOrderId,
                                        WorkOrderComponentLine componentLine,
                                        BigDecimal quantity,
                                        MaterialIssueLineRequest lineRequest) {
        BigDecimal remaining = componentLine.remainingQuantity();
        if (quantity.compareTo(remaining) <= 0) {
            return false;
        }
        throw ExceptionFactory.custom(BusinessErrorCode.OVER_BOM_APPROVAL_REQUIRED,
                "Over-BOM material must be requested and approved before stock is posted");
    }

    private MaterialIssueResponse requestOverBomIssue(WorkOrder workOrder,
                                                       MaterialIssuePostRequest request,
                                                       String normalizedKey) {
        Instant now = Instant.now();
        MaterialIssue issue = MaterialIssue.builder()
                .workOrder(workOrder)
                .status(MaterialIssueStatus.PENDING_APPROVAL)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(request))
                .traceId(traceIdProvider.currentTraceId())
                .note(support.trimToNull(request.note()))
                .supplemental(Boolean.TRUE.equals(request.supplemental()))
                .requestedAt(now)
                .build();
        Map<UUID, BigDecimal> requestedByComponent = requestedByComponent(workOrder, request);

        for (MaterialIssueLineRequest lineRequest : request.lines()) {
            WorkOrderComponentLine component = support.findComponentLine(workOrder, lineRequest.componentLineId());
            Warehouse warehouse = support.findActiveWarehouseInPlant(lineRequest.warehouseId(), workOrder.getPlant());
            BigDecimal quantity = support.requirePositive(lineRequest.quantity(), "Issue quantity");
            // Every line of a component that goes over is marked, the whole request judged together (G8b).
            boolean overIssue = requestedByComponent.get(component.getComponentLineId())
                    .compareTo(component.remainingQuantity()) > 0;
            if (overIssue && (!StringUtils.hasText(lineRequest.overrideReason()) || lineRequest.reasonCode() == null)) {
                throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                        "reasonCode and overrideReason are required for an Over-BOM request");
            }
            if (lineRequest.serialId() != null) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Serial-tracked Over-BOM material is deferred");
            }
            MaterialReservation reservation = lineRequest.reservationId() == null
                    ? null
                    : reservationService.findActiveReservationForIssue(
                            workOrder.getWorkOrderId(), lineRequest.reservationId());
            if (reservation != null) {
                validateReservationForIssue(reservation, component, warehouse, lineRequest, quantity);
            }
            requireLotWhenApprovalWillNeedOne(component, reservation, lineRequest);
            issue.getLines().add(MaterialIssueLine.builder()
                    .issue(issue)
                    .componentLine(component)
                    .reservation(reservation)
                    .item(component.getComponentItem())
                    .warehouse(warehouse)
                    .quantity(quantity)
                    .overIssue(overIssue)
                    .overrideReason(support.trimToNull(lineRequest.overrideReason()))
                    .requestedLotId(lineRequest.lotId())
                    .requestedLotCode(support.trimToNull(lineRequest.lotNumber()))
                    .requestedSerialId(lineRequest.serialId())
                    .issueReason(support.trimToNull(lineRequest.reason()))
                    .reasonCode(lineRequest.reasonCode())
                    .sourceExecution(resolveSourceExecution(workOrder, lineRequest.sourceExecutionId()))
                    .build());
        }
        ensureSourcesHoldEnough(workOrder, request);
        MaterialIssue saved = materialIssueRepository.saveAndFlush(issue);
        support.touchAndFlush(workOrder);
        return toResponse(saved);
    }

    /**
     * A lot-tracked component can only leave the warehouse from a named lot. An Over-BOM line has no
     * reservation to inherit one from, so if the request carries no lot the movement at approval time
     * is guaranteed to fail — and the request would sit in {@code PENDING_APPROVAL} forever, rejected
     * by a manager who cannot fix it and abandoned by the operator who could. Fail here instead
     * (rule C9), while the person who knows which lot they took is still on the screen.
     *
     * <p>The check does not replace the authoritative one inside {@code InventoryMovementService} at
     * approval: stock can change between request and decision, and that recheck runs in the posting
     * transaction. This one only stops a request that can never succeed from being stored at all.
     */
    private void requireLotWhenApprovalWillNeedOne(WorkOrderComponentLine component,
                                                   MaterialReservation reservation,
                                                   MaterialIssueLineRequest lineRequest) {
        if (reservation != null || !component.getComponentItem().isLotTracked()) {
            return;
        }
        if (lineRequest.lotId() == null && !StringUtils.hasText(lineRequest.lotNumber())) {
            throw ExceptionFactory.custom(ValidationErrorCode.LOT_REQUIRED,
                    "Over-BOM material for a lot-tracked component must name the lot it is taken from");
        }
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_APPROVED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse approve(UUID workOrderId, UUID issueId, MaterialIssueVersionedDecisionRequest request,
                                         String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(issueId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.ISSUE_APPROVE, fingerprint).isPresent()) {
            return toResponse(findIssue(issueId));
        }
        MaterialIssue issue = findPendingIssue(workOrderId, issueId);
        WorkOrder workOrder = issue.getWorkOrder();
        ensureVersions(issue, request);
        ReasonRules.lengthChecked(request.reason(), "reason");
        support.ensureExecutable(workOrder);
        // FE Q3: approving your own request needs OVERRIDE on top of APPROVE — never a role-name bypass.
        UUID actor = auditorAware == null ? null : auditorAware.getCurrentAuditor().orElse(null);
        if (actor != null && actor.equals(issue.getCreatedBy()) && !workOrderPermissionGuard.hasWorkOrderAccess(
                SecurityContextHolder.getContext().getAuthentication(), OVERRIDE_PERMISSION, workOrderId)) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Approving your own material issue request also requires PERM_MATERIAL_ISSUE_OVERRIDE");
        }
        idempotencyRecords.record(key, WorkOrderCommand.ISSUE_APPROVE, issueId, fingerprint);
        // Same as posting: change the work order once, before the movements can flush it.
        workOrder.markInProgress();
        workOrder.touch();
        BigDecimal totalIssued = BigDecimal.ZERO;
        int index = 0;
        for (MaterialIssueLine line : issue.getLines()) {
            index++;
            MaterialReservation reservation = line.getReservation() == null ? null
                    : reservationService.findActiveReservationForIssue(
                            workOrderId, line.getReservation().getReservationId());
            MaterialIssueLineRequest storedRequest = new MaterialIssueLineRequest(
                    line.getComponentLine().getComponentLineId(),
                    reservation == null ? null : reservation.getReservationId(),
                    line.getWarehouse().getWarehouseId(),
                    line.getRequestedLotId(), line.getRequestedLotCode(), line.getRequestedSerialId(),
                    line.getQuantity(), line.getIssueReason(), line.getOverrideReason(),
                    line.getReasonCode(),
                    line.getSourceExecution() == null ? null : line.getSourceExecution().getProductionExecutionId());
            if (reservation != null) {
                validateReservationForIssue(reservation, line.getComponentLine(), line.getWarehouse(),
                        storedRequest, line.getQuantity());
            }
            InventoryMovementResult result = reservation == null
                    ? movementService.issueManual(issueCommand(
                            line.getComponentLine(), line.getWarehouse(), line.getRequestedLotId(),
                            line.getRequestedLotCode(), line.getRequestedSerialId(), line.getQuantity(),
                            line.getIssueReason(), workOrder),
                            idempotency.childKey(issue.getIdempotencyKey() + ":approve", index))
                    : movementService.issueReservedManual(issueCommand(
                            line.getComponentLine(), line.getWarehouse(),
                            reservation.getLot() == null ? null : reservation.getLot().getLotId(),
                            null, line.getRequestedSerialId(), line.getQuantity(), line.getIssueReason(), workOrder),
                            idempotency.childKey(issue.getIdempotencyKey() + ":approve", index));
            StockMovement movement = result.movement();
            line.setStockMovement(movement);
            line.setLot(movement.getLot());
            line.setSerial(movement.getSerial());
            if (result.created()) {
                if (reservation != null) {
                    reservation.consume(line.getQuantity());
                }
                line.getComponentLine().addIssuedQuantity(line.getQuantity());
                workOrder.markInProgress();
                costAccumulatorService.accumulateMaterialCost(
                        workOrder, line.getComponentLine().getComponentItem(), line.getQuantity());
            }
            totalIssued = totalIssued.add(line.getQuantity());
        }
        issue.markPosted(Instant.now(), actor);
        MaterialIssue saved = materialIssueRepository.saveAndFlush(issue);
        wipTransactionService.recordMaterialIssued(workOrder, totalIssued, saved.getIssueId());
        support.flushTouched(workOrder);
        return toResponse(saved);
    }

    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_APPROVE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_REJECTED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse reject(UUID workOrderId, UUID issueId, MaterialIssueVersionedDecisionRequest request,
                                        String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(issueId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.ISSUE_REJECT, fingerprint).isPresent()) {
            return toResponse(findIssue(issueId));
        }
        MaterialIssue issue = findPendingIssue(workOrderId, issueId);
        String reason = requiredReason(request, "Reject reason is required");
        ensureVersions(issue, request);
        idempotencyRecords.record(key, WorkOrderCommand.ISSUE_REJECT, issueId, fingerprint);
        issue.reject(Instant.now(), auditorAware == null
                ? null : auditorAware.getCurrentAuditor().orElse(null), reason);
        MaterialIssue saved = materialIssueRepository.saveAndFlush(issue);
        support.touchAndFlush(issue.getWorkOrder());
        return toResponse(saved);
    }

    /**
     * The requester withdraws their own pending request (G8b, FE §1.4): {@code PENDING_APPROVAL →
     * CANCELLED}, a reason, no stock effect — nothing was ever posted. Anyone else, even with
     * {@code MANAGE}, is refused: a manager declines a request by rejecting it.
     */
    @Transactional
    @PreAuthorize("@workOrderPermissionGuard.hasWorkOrderAccess(authentication, 'PERM_MATERIAL_ISSUE_MANAGE', #workOrderId)")
    @Auditable(action = AuditAction.MATERIAL_ISSUE_CANCELLED, entityType = "MaterialIssue", entityIdExpression = "issueId.toString()")
    public MaterialIssueResponse cancelByRequester(UUID workOrderId, UUID issueId,
                                                   MaterialIssueVersionedDecisionRequest request, String idempotencyKey) {
        String key = idempotencyRecords.key(idempotencyKey);
        WorkOrderIdempotency.Fingerprint fingerprint = new WorkOrderIdempotency.Fingerprint(issueId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.ISSUE_CANCEL, fingerprint).isPresent()) {
            return toResponse(findIssue(issueId));
        }
        MaterialIssue issue = findPendingIssue(workOrderId, issueId);
        String reason = requiredReason(request, "Cancel reason is required");
        ensureVersions(issue, request);
        UUID actor = auditorAware == null ? null : auditorAware.getCurrentAuditor().orElse(null);
        if (actor == null || !actor.equals(issue.getCreatedBy())) {
            throw ExceptionFactory.custom(AuthErrorCode.ACCESS_DENIED,
                    "Only the person who made the request can cancel it; a manager rejects it instead");
        }
        idempotencyRecords.record(key, WorkOrderCommand.ISSUE_CANCEL, issueId, fingerprint);
        issue.cancelByRequester(Instant.now(), actor, reason);
        MaterialIssue saved = materialIssueRepository.saveAndFlush(issue);
        support.touchAndFlush(issue.getWorkOrder());
        return toResponse(saved);
    }

    /** Missing ⇒ the specific {@code APPROVAL_REASON_REQUIRED}; present but outside 5–2000 ⇒ field error (Q33). */
    private String requiredReason(MaterialIssueVersionedDecisionRequest request, String missingMessage) {
        String reason = ReasonRules.lengthChecked(request == null ? null : request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.APPROVAL_REASON_REQUIRED, missingMessage);
        }
        return reason;
    }

    /** Both versions read together on the decision screen (D5): the work order's and the issue's. */
    private void ensureVersions(MaterialIssue issue, MaterialIssueVersionedDecisionRequest request) {
        WorkOrderVersions.ensure(issue.getWorkOrder(), request == null ? null : request.expectedWorkOrderVersion(),
                "expectedWorkOrderVersion");
        if (request.expectedIssueVersion() == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("expectedIssueVersion", "must not be null"));
        }
        if (!Objects.equals(issue.getVersion(), request.expectedIssueVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION, "Material issue version is "
                    + issue.getVersion() + ", not " + request.expectedIssueVersion() + "; reload it");
        }
    }

    private MaterialIssue findIssue(UUID issueId) {
        return materialIssueRepository.findWithLinesByIssueId(issueId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material issue", issueId));
    }

    private MaterialIssue findPendingIssue(UUID workOrderId, UUID issueId) {
        MaterialIssue issue = materialIssueRepository.findWithLinesByIssueId(issueId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Material issue", issueId));
        if (!issue.getWorkOrder().getWorkOrderId().equals(workOrderId) || !issue.isPendingApproval()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only a PENDING_APPROVAL issue of this work order can be decided");
        }
        return issue;
    }

    private ProductionExecution resolveSourceExecution(WorkOrder workOrder, UUID executionId) {
        if (executionId == null || productionExecutionRepository == null) {
            return null;
        }
        ProductionExecution execution = productionExecutionRepository.findById(executionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Production execution", executionId));
        if (!execution.getWorkOrder().getWorkOrderId().equals(workOrder.getWorkOrderId())
                || execution.getReworkQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "sourceExecutionId must reference a rework execution of the same work order");
        }
        return execution;
    }

    private InventoryIssueCommand issueCommand(WorkOrderComponentLine componentLine,
                                               Warehouse warehouse,
                                               UUID lotId,
                                               String lotCode,
                                               UUID serialId,
                                               BigDecimal quantity,
                                               String reason,
                                               WorkOrder workOrder) {
        return new InventoryIssueCommand(
                componentLine.getComponentItem().getItemId(),
                warehouse.getWarehouseId(),
                lotId,
                lotCode,
                quantity,
                support.trimToNull(reason),
                WorkOrderExecutionSupport.WORK_ORDER_REFERENCE_TYPE,
                workOrder.getWorkOrderId().toString(),
                serialId,
                null);
    }

    private void validateReservationForIssue(MaterialReservation reservation,
                                             WorkOrderComponentLine componentLine,
                                             Warehouse warehouse,
                                             MaterialIssueLineRequest lineRequest,
                                             BigDecimal quantity) {
        if (!reservation.getComponentLine().getComponentLineId().equals(componentLine.getComponentLineId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation does not belong to the requested component line");
        }
        if (!reservation.getWarehouse().getWarehouseId().equals(warehouse.getWarehouseId())) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation warehouse does not match issue warehouse");
        }
        UUID reservationLotId = reservation.getLot() != null ? reservation.getLot().getLotId() : null;
        if (lineRequest.lotId() != null && !lineRequest.lotId().equals(reservationLotId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Reservation lot does not match issue lot");
        }
        if (quantity.compareTo(reservation.remainingQuantity()) > 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.RESERVATION_EXCEEDED,
                    "Issue quantity exceeds remaining reservation quantity");
        }
    }
}
