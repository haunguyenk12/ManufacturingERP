package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCopyRequest;
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftSaveRequest;
import com.erp.manufacturing.module.routing.dto.RoutingOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoutingService {

    /** Above any real sequence, so surviving operations can step aside while a graph is re-saved. */
    private static final int PARKING_SEQUENCE_BASE = 1_000_000;

    private final RoutingHeaderRepository routingHeaderRepository;
    private final ItemLookupService itemLookupService;
    private final WorkCenterLookupService workCenterLookupService;
    private final RoutingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.ROUTING_CREATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()")
    public RoutingResponse create(UUID companyId, RoutingCreateRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureManufacturableItem(item);
        ensureItemBelongsToCompany(item, companyId);

        String code = normalize(request.code(), "Routing code");
        String version = normalize(request.version(), "Routing version");
        if (routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, code, version)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Routing", code + "/" + version);
        }

        // G5: the version number is taken under the family lock, like every other create path.
        itemLookupService.lockItem(item.getItemId());
        RoutingHeader routing = RoutingHeader.builder()
                .company(item.getCompany())
                .item(item)
                .code(code)
                .routingVersion(version)
                .versionNumber(nextVersionNumber(companyId, item.getItemId()))
                .status(RoutingStatus.DRAFT)
                .note(trimToNull(request.note()))
                .build();

        // Resolve every work center and validate B_wc2 (same plant across all operations) BEFORE
        // building any entity (rule C9) — a routing whose operations span two plants must never
        // reach the repository.
        Set<Integer> sequences = new HashSet<>();
        Map<Integer, WorkCenter> workCentersBySequence = new LinkedHashMap<>();
        for (RoutingOperationRequest operationRequest : request.operations()) {
            if (!sequences.add(operationRequest.sequence())) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                        "Duplicate operation sequence: " + operationRequest.sequence());
            }
            workCentersBySequence.put(operationRequest.sequence(),
                    workCenterLookupService.getActiveWorkCenter(operationRequest.workCenterId()));
        }
        ensureOperationsShareOnePlant(workCentersBySequence.values());

        for (RoutingOperationRequest operationRequest : request.operations()) {
            routing.getOperations().add(buildOperation(
                    routing, operationRequest, workCentersBySequence.get(operationRequest.sequence())));
        }
        return mapper.toResponse(routingHeaderRepository.save(routing));
    }

    /**
     * Atomic save of a routing draft and its whole operation graph (G5, FE R09): header, operations and
     * predecessor links in this one transaction. Every rule — unique sequence and client key,
     * predecessors that exist and come earlier, ACTIVE work centers of one plant — is checked before
     * anything is written (rule C9).
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_READ', 'COMPANY', #companyId)"
            + " and @permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.ROUTING_DRAFT_SAVED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()", changeMode = AuditChangeMode.NONE, operation = AuditOperation.UPDATE)
    public RoutingResponse saveDraft(UUID companyId, RoutingDraftSaveRequest request) {
        String code = normalize(request.code(), "Routing code");
        GraphPlan plan = planGraph(request.operations());
        return request.routingId() == null
                ? createDraft(companyId, request, code, plan)
                : replaceDraft(companyId, request, code, plan);
    }

    /**
     * New DRAFT of the same product and code from any version (G5). Refused while the product already
     * has an open draft (FE Q18) — the user should open that one rather than fork a second.
     */
    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_READ', #routingId)"
            + " and @routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_COPIED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()", changeMode = AuditChangeMode.NONE, operation = AuditOperation.CREATE)
    public RoutingResponse copy(UUID routingId, RoutingCopyRequest request) {
        UUID itemId = routingHeaderRepository.findItemIdByRoutingId(routingId)
                .orElseThrow(() -> notFound(routingId));
        itemLookupService.lockItem(itemId);
        RoutingHeader source = findRouting(routingId);
        UUID companyId = source.getCompany().getCompanyId();
        ensureNoOpenDraft(companyId, itemId);

        int versionNumber = nextVersionNumber(companyId, itemId);
        RoutingHeader copy = RoutingHeader.builder()
                .company(source.getCompany())
                .item(source.getItem())
                .code(source.getCode())
                .routingVersion(versionLabel(request.version(), versionNumber, companyId, source.getCode()))
                .versionNumber(versionNumber)
                .name(request.name() == null ? source.getName() : trimToNull(request.name()))
                .effectiveFrom(request.effectiveFrom())
                .note(request.note() == null ? source.getNote() : trimToNull(request.note()))
                .status(RoutingStatus.DRAFT)
                .build();
        Map<RoutingOperation, RoutingOperation> copies = new HashMap<>();
        for (RoutingOperation operation : source.getOperations()) {
            RoutingOperation copied = RoutingOperation.builder()
                    .routing(copy)
                    .sequence(operation.getSequence())
                    .name(operation.getName())
                    .workCenter(operation.getWorkCenter())
                    .setupMinutes(operation.getSetupMinutes())
                    .runMinutesPerUnit(operation.getRunMinutesPerUnit())
                    .build();
            copies.put(operation, copied);
            copy.getOperations().add(copied);
        }
        copies.forEach((original, copied) -> original.getPredecessors()
                .forEach(predecessor -> copied.getPredecessors().add(copies.get(predecessor))));
        return mapper.toResponse(routingHeaderRepository.saveAndFlush(copy));
    }

    /** Every version of the routing's product in its company, newest version first (G5). */
    @Transactional(readOnly = true)
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_READ', #routingId)")
    public PageResult<RoutingResponse> listVersions(UUID routingId, int page, int size) {
        RoutingHeader routing = routingHeaderRepository.findById(routingId).orElseThrow(() -> notFound(routingId));
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("versionNumber"), Sort.Order.desc("routingId")));
        return PageResult.from(routingHeaderRepository.findByCompanyCompanyIdAndItemItemId(
                        routing.getCompany().getCompanyId(), routing.getItem().getItemId(), pageable)
                .map(mapper::toResponse));
    }

    /**
     * Activating makes this the one routing production will snapshot for the item, so the previous
     * {@code ACTIVE} revision of the same item is stood down in the same transaction — same rule as
     * {@code BomService.activateBom} (invariant B7). G5: under the item lock, so two concurrent
     * activations of one product queue instead of one failing on {@code uk_routings_one_active_per_item}.
     */
    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_ACTIVATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()")
    public RoutingResponse activate(UUID routingId, Long expectedVersion) {
        UUID itemId = routingHeaderRepository.findItemIdByRoutingId(routingId).orElseThrow(() -> notFound(routingId));
        itemLookupService.lockItem(itemId);
        RoutingHeader candidate = findRouting(routingId);
        if (expectedVersion != null) {
            ensureVersion(candidate, expectedVersion);
        }
        if (!candidate.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.STATE_CONFLICT,
                    "Only DRAFT routings can be activated");
        }
        if (candidate.getOperations().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.DOCUMENT_HAS_NO_LINES,
                    "Cannot activate routing without operations");
        }
        if (candidate.getEffectiveFrom() != null && candidate.getEffectiveFrom().isAfter(Instant.now())) {
            throw ExceptionFactory.custom(BusinessErrorCode.EFFECTIVE_DATE_FUTURE,
                    "Routing is effective from " + candidate.getEffectiveFrom() + "; it cannot be activated before then");
        }
        for (RoutingOperation operation : candidate.getOperations()) {
            if (operation.getWorkCenter() != null && !operation.getWorkCenter().isActive()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Work center " + operation.getWorkCenter().getCode() + " is inactive");
            }
        }

        routingHeaderRepository.findByCompanyCompanyIdAndItemItemIdAndStatus(
                        candidate.getCompany().getCompanyId(),
                        candidate.getItem().getItemId(),
                        RoutingStatus.ACTIVE)
                .filter(active -> !active.getRoutingId().equals(candidate.getRoutingId()))
                .ifPresent(active -> {
                    active.deactivate();
                    routingHeaderRepository.saveAndFlush(active);
                });

        candidate.activate();
        return mapper.toResponse(routingHeaderRepository.saveAndFlush(candidate));
    }

    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_DEACTIVATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()")
    public RoutingResponse deactivate(UUID routingId) {
        RoutingHeader routing = findRouting(routingId);
        routing.deactivate();
        return mapper.toResponse(routingHeaderRepository.save(routing));
    }

    /** {@code POST /routings/{id}/deactivate} (G5): the DELETE transition, guarded by {@code expectedVersion}. */
    @Transactional
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_READ', #routingId)"
            + " and @routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_MANAGE', #routingId)")
    @Auditable(action = AuditAction.ROUTING_DEACTIVATED, entityType = "RoutingHeader", entityIdExpression = "routingId.toString()",
               companyId = "#result?.companyId()")
    public RoutingResponse deactivate(UUID routingId, long expectedVersion) {
        RoutingHeader routing = findRouting(routingId);
        ensureVersion(routing, expectedVersion);
        routing.deactivate();
        return mapper.toResponse(routingHeaderRepository.saveAndFlush(routing));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ROUTING_READ', 'COMPANY', #companyId)")
    public PageResult<RoutingResponse> list(UUID companyId, UUID itemId, RoutingStatus status, UUID plantId,
                                            UUID workCenterId, String search, Pageable pageable) {
        return PageResult.from(routingHeaderRepository.search(companyId, itemId, status, plantId, workCenterId,
                        StringUtils.hasText(search) ? search.trim() : null, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@routingPermissionGuard.hasRoutingAccess(authentication, 'PERM_ROUTING_READ', #routingId)")
    public RoutingResponse get(UUID routingId) {
        return mapper.toResponse(findRouting(routingId));
    }

    private RoutingResponse createDraft(UUID companyId, RoutingDraftSaveRequest request, String code, GraphPlan plan) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureManufacturableItem(item);
        ensureItemBelongsToCompany(item, companyId);
        Map<String, WorkCenter> workCenters = resolveWorkCenters(request.operations());

        itemLookupService.lockItem(item.getItemId());
        ensureNoOpenDraft(companyId, item.getItemId());
        if (routingHeaderRepository.existsCodeOnOtherItem(companyId, code, item.getItemId())) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Routing code of another product", code);
        }
        int versionNumber = nextVersionNumber(companyId, item.getItemId());
        RoutingHeader routing = RoutingHeader.builder()
                .company(item.getCompany())
                .item(item)
                .code(code)
                .routingVersion(versionLabel(request.version(), versionNumber, companyId, code))
                .versionNumber(versionNumber)
                .name(trimToNull(request.name()))
                .effectiveFrom(request.effectiveFrom())
                .note(trimToNull(request.note()))
                .status(RoutingStatus.DRAFT)
                .build();
        applyGraph(routing, request.operations(), workCenters, plan, Map.of());
        return mapper.toResponse(routingHeaderRepository.saveAndFlush(routing));
    }

    private RoutingResponse replaceDraft(UUID companyId, RoutingDraftSaveRequest request, String code, GraphPlan plan) {
        if (request.expectedVersion() == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("expectedVersion", "is required when routingId is given"));
        }
        RoutingHeader routing = routingHeaderRepository.findWithOperationsByRoutingId(request.routingId())
                // Another company's routing is answered exactly like a missing one.
                .filter(found -> found.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> notFound(request.routingId()));
        ensureVersion(routing, request.expectedVersion());
        if (!routing.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, "Only DRAFT routings can be changed");
        }
        Map<String, String> immutable = new LinkedHashMap<>();
        if (!routing.getItem().getItemId().equals(request.itemId())) {
            immutable.put("itemId", "cannot change; copy the routing to make one for another product");
        }
        if (!routing.getCode().equals(code)) {
            immutable.put("code", "cannot change; the code names the product's routing family");
        }
        Map<UUID, RoutingOperation> existingById = new HashMap<>();
        routing.getOperations().forEach(operation -> existingById.put(operation.getRoutingOperationId(), operation));
        for (int index = 0; index < request.operations().size(); index++) {
            UUID operationId = request.operations().get(index).routingOperationId();
            if (operationId != null && !existingById.containsKey(operationId)) {
                immutable.put("operations[" + index + "].routingOperationId", "is not an operation of this routing");
            }
        }
        if (!immutable.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, immutable);
        }
        Map<String, WorkCenter> workCenters = resolveWorkCenters(request.operations());
        String label = request.version() == null || normalize(request.version(), "Routing version")
                .equals(routing.getRoutingVersion())
                ? routing.getRoutingVersion()
                : versionLabel(request.version(), routing.getVersionNumber(), companyId, code);

        applyGraph(routing, request.operations(), workCenters, plan, existingById);
        routing.setRoutingVersion(label);
        routing.setName(trimToNull(request.name()));
        routing.setEffectiveFrom(request.effectiveFrom());
        routing.setNote(trimToNull(request.note()));
        // Operations are an inverse collection — see BomService.replaceDraft for why this bumps the version once.
        routing.setUpdatedAt(Instant.now());
        return mapper.toResponse(routingHeaderRepository.saveAndFlush(routing));
    }

    /**
     * Validates the graph shape of a request with no database access: client keys and sequences unique,
     * predecessors that name another row of the request with a smaller sequence. A smaller sequence on
     * every edge is also why no cycle check is needed. All problems come back together, by field.
     */
    private GraphPlan planGraph(List<RoutingDraftOperationRequest> operations) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Integer> sequenceByKey = new HashMap<>();
        Set<Integer> sequences = new HashSet<>();
        for (int index = 0; index < operations.size(); index++) {
            RoutingDraftOperationRequest operation = operations.get(index);
            if (sequenceByKey.putIfAbsent(operation.clientKey(), operation.sequence()) != null) {
                errors.put("operations[" + index + "].clientKey", "is used by another operation");
            }
            if (!sequences.add(operation.sequence())) {
                errors.put("operations[" + index + "].sequence", "is used by another operation");
            }
        }
        for (int index = 0; index < operations.size(); index++) {
            RoutingDraftOperationRequest operation = operations.get(index);
            List<String> predecessors = operation.predecessorClientKeys() == null
                    ? List.of() : operation.predecessorClientKeys();
            Set<String> seen = new HashSet<>();
            for (String predecessor : predecessors) {
                String field = "operations[" + index + "].predecessorClientKeys";
                if (!seen.add(predecessor)) {
                    errors.put(field, "lists '" + predecessor + "' twice");
                } else if (predecessor.equals(operation.clientKey())) {
                    errors.put(field, "an operation cannot precede itself");
                } else if (!sequenceByKey.containsKey(predecessor)) {
                    errors.put(field, "'" + predecessor + "' is not an operation of this request");
                } else if (sequenceByKey.get(predecessor) >= operation.sequence()) {
                    errors.put(field, "'" + predecessor + "' must have a smaller sequence");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        return new GraphPlan(sequenceByKey);
    }

    /** ACTIVE work centers, all of one plant ({@code B_wc2}), keyed by the request row's client key. */
    private Map<String, WorkCenter> resolveWorkCenters(List<RoutingDraftOperationRequest> operations) {
        Map<String, WorkCenter> byKey = new LinkedHashMap<>();
        for (RoutingDraftOperationRequest operation : operations) {
            byKey.put(operation.clientKey(), workCenterLookupService.getActiveWorkCenter(operation.workCenterId()));
        }
        ensureOperationsShareOnePlant(byKey.values());
        return byKey;
    }

    /**
     * Writes the requested graph onto {@code routing}. Operations named by id keep it; the rest of the
     * existing ones are removed. Survivors are parked above any real sequence and flushed before their
     * final sequence is set, because {@code uk_routing_operations_sequence} is checked row by row and a
     * swap would collide inside one flush (the BOM line lesson, §0.40 #1).
     */
    private void applyGraph(RoutingHeader routing, List<RoutingDraftOperationRequest> requested,
                            Map<String, WorkCenter> workCenters, GraphPlan plan,
                            Map<UUID, RoutingOperation> existingById) {
        Set<UUID> keptIds = new HashSet<>();
        requested.stream().map(RoutingDraftOperationRequest::routingOperationId)
                .filter(Objects::nonNull).forEach(keptIds::add);

        if (!existingById.isEmpty()) {
            // Drop every predecessor link first: a removed operation may be someone's predecessor.
            routing.getOperations().forEach(operation -> operation.getPredecessors().clear());
            routing.getOperations().removeIf(operation -> !keptIds.contains(operation.getRoutingOperationId()));
            routingHeaderRepository.flush();
            int parking = PARKING_SEQUENCE_BASE;
            for (RoutingOperation operation : routing.getOperations()) {
                operation.setSequence(parking++);
            }
            routingHeaderRepository.flush();
        }

        Map<String, RoutingOperation> byKey = new HashMap<>();
        for (RoutingDraftOperationRequest request : requested) {
            RoutingOperation operation = request.routingOperationId() == null
                    ? null : existingById.get(request.routingOperationId());
            if (operation == null) {
                operation = RoutingOperation.builder().routing(routing).build();
                routing.getOperations().add(operation);
            }
            operation.setSequence(plan.sequenceByKey().get(request.clientKey()));
            operation.setName(request.name().trim());
            operation.setWorkCenter(workCenters.get(request.clientKey()));
            operation.setSetupMinutes(request.setupMinutes());
            operation.setRunMinutesPerUnit(request.runMinutesPerUnit());
            byKey.put(request.clientKey(), operation);
        }
        for (RoutingDraftOperationRequest request : requested) {
            if (request.predecessorClientKeys() == null) {
                continue;
            }
            RoutingOperation operation = byKey.get(request.clientKey());
            request.predecessorClientKeys().forEach(key -> operation.getPredecessors().add(byKey.get(key)));
        }
    }

    private void ensureNoOpenDraft(UUID companyId, UUID itemId) {
        if (routingHeaderRepository.existsByCompanyCompanyIdAndItemItemIdAndStatus(companyId, itemId, RoutingStatus.DRAFT)) {
            throw ExceptionFactory.custom(BusinessErrorCode.OPEN_DRAFT_EXISTS,
                    "This product already has a DRAFT routing; open it instead of creating another");
        }
    }

    private void ensureVersion(RoutingHeader routing, long expectedVersion) {
        if (!Objects.equals(routing.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Routing was modified by another request; reload it");
        }
    }

    /** Caller holds the item lock — see {@link ItemLookupService#lockItem}. */
    private int nextVersionNumber(UUID companyId, UUID itemId) {
        return routingHeaderRepository.findMaxVersionNumber(companyId, itemId) + 1;
    }

    /** The requested label or the version number; free within the code ({@code uk_routings_company_code_version}). */
    private String versionLabel(String requested, int versionNumber, UUID companyId, String code) {
        String label = StringUtils.hasText(requested)
                ? normalize(requested, "Routing version")
                : String.valueOf(versionNumber);
        if (routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, code, label)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Routing", code + "/" + label);
        }
        return label;
    }

    private RoutingOperation buildOperation(RoutingHeader routing, RoutingOperationRequest request, WorkCenter workCenter) {
        return RoutingOperation.builder()
                .routing(routing)
                .sequence(request.sequence())
                .name(request.name().trim())
                .workCenter(workCenter)
                .setupMinutes(request.setupMinutes())
                .runMinutesPerUnit(request.runMinutesPerUnit())
                .build();
    }

    /**
     * Bất biến B_wc2 (module/workcenter/CLAUDE.md): every operation in one routing must reference a
     * work center of the same plant. Routing itself stays company-level (no schema change), so this
     * is the only place that enforces the consequence of Work Center being per-plant — 422, not 409:
     * it is invalid <em>input</em>, not a status-machine conflict (error-handling.md §5.3).
     */
    private void ensureOperationsShareOnePlant(Collection<WorkCenter> workCenters) {
        long distinctPlants = workCenters.stream()
                .map(workCenter -> workCenter.getPlant().getPlantId())
                .distinct()
                .count();
        if (distinctPlants > 1) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "All routing operations must reference work centers of the same plant");
        }
    }

    private RoutingHeader findRouting(UUID routingId) {
        return routingHeaderRepository.findWithOperationsByRoutingId(routingId)
                .orElseThrow(() -> notFound(routingId));
    }

    private RuntimeException notFound(UUID routingId) {
        return ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Routing", routingId);
    }

    private void ensureManufacturableItem(Item item) {
        if (item.getType() != ItemType.WIP && item.getType() != ItemType.FINISHED_GOOD) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Routing item must be WIP or FINISHED_GOOD");
        }
    }

    private void ensureItemBelongsToCompany(Item item, UUID companyId) {
        if (!item.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to the routing company");
        }
    }

    private String normalize(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Sequence of each request row, by client key — the request's graph after shape validation. */
    private record GraphPlan(Map<String, Integer> sequenceByKey) {
    }
}
