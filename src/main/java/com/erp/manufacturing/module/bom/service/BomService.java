package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditOperation;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.*;
import com.erp.manufacturing.module.bom.mapper.BomMapper;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BomService {

    private static final BigDecimal SCRAP_RATE_UPPER_BOUND = BigDecimal.ONE;
    private static final Pattern BOM_CODE_PATTERN = Pattern.compile("^[A-Z0-9._-]+$");
    private static final int QUANTITY_SCALE = 6;

    private final BomHeaderRepository bomHeaderRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemLookupService itemLookupService;
    private final BomMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.BOM_CREATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()")
    public BomResponse createBom(UUID companyId, BomCreateRequest request) {
        Item parentItem = itemLookupService.getActiveItem(request.parentItemId());
        ensureParentItemAllowed(parentItem);
        ensureItemBelongsToCompany(parentItem, companyId);
        ensureCompanyActive(parentItem);

        String revision = normalizeRevision(request.revision());
        if (bomHeaderRepository.existsByCompanyCompanyIdAndParentItemItemIdAndRevision(
                companyId, parentItem.getItemId(), revision)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "BOM revision", revision);
        }

        // G5: the old header endpoint carries no BOM code, so it gets the "product-revision" code V73
        // backfilled; the version number is taken under the family lock like every other path.
        itemLookupService.lockItem(parentItem.getItemId());
        int versionNumber = nextVersionNumber(companyId, parentItem.getItemId());
        BomHeader bom = BomHeader.builder()
                .company(parentItem.getCompany())
                .parentItem(parentItem)
                .revision(revision)
                .bomCode(derivedBomCode(companyId, parentItem, revision, versionNumber))
                .versionNumber(versionNumber)
                .description(trimToNull(request.description()))
                .status(BomStatus.DRAFT)
                .build();
        return mapper.toResponse(bomHeaderRepository.save(bom));
    }

    /**
     * Atomic save of a whole draft (G5, D4, FE R02): header and every line in this one transaction —
     * a failure on the last line leaves no header and no line behind. Create when {@code bomId} is
     * absent, full replacement of a DRAFT otherwise.
     *
     * <p>Every line is validated before anything is written (rule C9). Lines are matched to the draft's
     * existing lines by component, so a component that stays keeps its line id.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_READ', 'COMPANY', #companyId)"
            + " and @permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.BOM_DRAFT_SAVED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()", changeMode = AuditChangeMode.NONE, operation = AuditOperation.UPDATE)
    public BomResponse saveDraft(UUID companyId, BomDraftSaveRequest request) {
        String bomCode = normalizeBomCode(request.bomCode());
        checkLineShape(request);
        return request.bomId() == null
                ? createDraft(companyId, request, bomCode)
                : replaceDraft(companyId, request, bomCode);
    }

    /**
     * New DRAFT of the same product from any version (G5, FE R04). The source is never touched, and
     * neither is any work order snapshot — those were copied away at work order creation (B12).
     */
    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)"
            + " and @bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_COPIED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()", changeMode = AuditChangeMode.NONE, operation = AuditOperation.CREATE)
    public BomResponse copyBom(UUID bomId, BomCopyRequest request) {
        String bomCode = normalizeBomCode(request.bomCode());
        UUID parentItemId = bomHeaderRepository.findParentItemIdByBomId(bomId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM", bomId));
        itemLookupService.lockItem(parentItemId);
        BomHeader source = findBomWithLines(bomId);
        UUID companyId = source.getCompany().getCompanyId();
        ensureBomCodeFree(companyId, bomCode, null);

        int versionNumber = nextVersionNumber(companyId, parentItemId);
        BomHeader copy = BomHeader.builder()
                .company(source.getCompany())
                .parentItem(source.getParentItem())
                .revision(revisionFor(request.revision(), versionNumber, companyId, parentItemId, null))
                .bomCode(bomCode)
                .versionNumber(versionNumber)
                .outputQuantity(source.getOutputQuantity())
                .effectiveFrom(request.effectiveFrom())
                .description(request.description() == null ? source.getDescription() : trimToNull(request.description()))
                .status(BomStatus.DRAFT)
                .build();
        for (BomLine line : source.getLines()) {
            copy.getLines().add(BomLine.builder()
                    .bom(copy)
                    .componentItem(line.getComponentItem())
                    .lineNo(line.getLineNo())
                    .quantityPer(line.getQuantityPer())
                    .scrapRate(line.getScrapRate())
                    .build());
        }
        return mapper.toResponse(bomHeaderRepository.saveAndFlush(copy));
    }

    /** Every version of the BOM's product in its company, newest version first (G5). */
    @Transactional(readOnly = true)
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)")
    public PageResult<BomResponse> listVersions(UUID bomId, int page, int size) {
        BomHeader bom = bomHeaderRepository.findById(bomId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM", bomId));
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("versionNumber"), Sort.Order.desc("bomId")));
        Page<BomHeader> versions = bomHeaderRepository.findByCompanyCompanyIdAndParentItemItemId(
                bom.getCompany().getCompanyId(), bom.getParentItem().getItemId(), pageable);
        return PageResult.from(versions.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_READ', 'COMPANY', #companyId)")
    public PageResult<BomResponse> listBoms(UUID companyId, UUID parentItemId, BomStatus status, String search,
                                            boolean latestOnly, Pageable pageable) {
        return PageResult.from(bomHeaderRepository.search(companyId, parentItemId, status,
                StringUtils.hasText(search) ? search.trim() : null, latestOnly, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)")
    public BomResponse getBom(UUID bomId) {
        return mapper.toResponse(findBomWithLines(bomId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@bomPermissionGuard.hasItemBomAccess(authentication, 'PERM_BOM_READ', #itemId)")
    public BomResponse getActiveBomByItem(UUID itemId) {
        Item item = itemLookupService.getItem(itemId);
        BomHeader bom = bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                        item.getCompany().getCompanyId(), itemId, BomStatus.ACTIVE)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "Active BOM", itemId));
        return mapper.toResponse(bom);
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()")
    public BomResponse updateBom(UUID bomId, BomUpdateRequest request) {
        BomHeader bom = findBomWithLines(bomId);
        ensureDraft(bom);

        String revision = normalizeRevision(request.revision());
        if (!bom.getRevision().equals(revision)
                && bomHeaderRepository.existsByCompanyCompanyIdAndParentItemItemIdAndRevision(
                bom.getCompany().getCompanyId(), bom.getParentItem().getItemId(), revision)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "BOM revision", revision);
        }

        bom.setRevision(revision);
        bom.setDescription(trimToNull(request.description()));
        return mapper.toResponse(bomHeaderRepository.save(bom));
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_DELETED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()")
    public BomResponse deactivateBom(UUID bomId) {
        BomHeader bom = findBomWithLines(bomId);
        bom.deactivate();
        return mapper.toResponse(bomHeaderRepository.save(bom));
    }

    /**
     * {@code POST /boms/{id}/deactivate} (G5): the same transition as {@code DELETE}, guarded by the
     * caller's {@code expectedVersion}. A separate public method rather than a call into
     * {@link #deactivateBom(UUID)} — going through {@code this} would bypass the proxy, and with it the
     * permission check and the audit event (CLAUDE.md §0.19 #3).
     */
    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)"
            + " and @bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_DELETED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()")
    public BomResponse deactivateBom(UUID bomId, long expectedVersion) {
        BomHeader bom = findBomWithLines(bomId);
        ensureVersion(bom, expectedVersion);
        bom.deactivate();
        return mapper.toResponse(bomHeaderRepository.saveAndFlush(bom));
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    // CUSTOM: the header's own scalar fields do not move when a line is added, so the generic differ
    // would report zero changes for a command that changed the BOM materially.
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               changeMode = com.erp.manufacturing.common.audit.model.AuditChangeMode.CUSTOM,
               operation = com.erp.manufacturing.common.audit.model.AuditOperation.UPDATE,
               companyId = "#result?.companyId()")
    public BomResponse addLine(UUID bomId, BomLineCreateRequest request) {
        BomHeader bom = findBomWithLines(bomId);
        ensureDraft(bom);
        Item componentItem = itemLookupService.getActiveItem(request.componentItemId());
        validateLineForBom(bom, componentItem, request.lineNo(), request.quantityPer(), request.scrapRate());

        if (bomLineRepository.existsByBomBomIdAndComponentItemItemId(bomId, componentItem.getItemId())) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "BOM component", componentItem.getCode());
        }
        if (bomLineRepository.existsByBomBomIdAndLineNo(bomId, request.lineNo())) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "BOM line number", request.lineNo());
        }

        BomLine line = BomLine.builder()
                .bom(bom)
                .componentItem(componentItem)
                .lineNo(request.lineNo())
                .quantityPer(request.quantityPer())
                .scrapRate(request.scrapRate())
                .build();
        bom.getLines().add(line);
        bomHeaderRepository.save(bom);
        return mapper.toResponse(findBomWithLines(bomId));
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomLineAccess(authentication, 'PERM_BOM_MANAGE', #lineId)")
    // Was entityIdExpression = "bomId.toString()" on a BomResponse: it recorded the HEADER id under
    // entityType "BomLine", pointing an investigator confidently at the wrong row.
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomLine",
               entityId = "#lineId",
               changeMode = com.erp.manufacturing.common.audit.model.AuditChangeMode.CUSTOM,
               operation = com.erp.manufacturing.common.audit.model.AuditOperation.UPDATE,
               companyId = "#result?.companyId()")
    public BomResponse updateLine(UUID lineId, BomLineUpdateRequest request) {
        BomLine line = findLine(lineId);
        BomHeader bom = line.getBom();
        ensureDraft(bom);
        Item componentItem = itemLookupService.getActiveItem(request.componentItemId());
        validateLineForBom(bom, componentItem, request.lineNo(), request.quantityPer(), request.scrapRate());

        if (bomLineRepository.existsDuplicateComponent(bom.getBomId(), componentItem.getItemId(), lineId)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "BOM component", componentItem.getCode());
        }
        if (bomLineRepository.existsDuplicateLineNo(bom.getBomId(), request.lineNo(), lineId)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS,
                    "BOM line number", request.lineNo());
        }

        line.setComponentItem(componentItem);
        line.setLineNo(request.lineNo());
        line.setQuantityPer(request.quantityPer());
        line.setScrapRate(request.scrapRate());
        bomLineRepository.save(line);
        return mapper.toResponse(findBomWithLines(bom.getBomId()));
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomLineAccess(authentication, 'PERM_BOM_MANAGE', #lineId)")
    // BomLineAuditProvider supplies both targets (header as primary, line as related) and the real
    // line diff; CUSTOM keeps the generic differ from claiming "no changes" for a removed line.
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomLine",
               entityId = "#lineId",
               changeMode = com.erp.manufacturing.common.audit.model.AuditChangeMode.CUSTOM,
               operation = com.erp.manufacturing.common.audit.model.AuditOperation.DELETE)
    public void deleteLine(UUID lineId) {
        BomLine line = findLine(lineId);
        ensureDraft(line.getBom());
        bomLineRepository.delete(line);
    }

    /**
     * {@code expectedVersion} is optional because the endpoint predates it (G5 kept the old contract);
     * when sent, a stale value refuses the activation.
     */
    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_ACTIVATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()",
               companyId = "#result?.companyId()")
    public BomResponse activateBom(UUID bomId, Long expectedVersion) {
        // Family lock first (G5, FE R03): two activations of the same product queue here, so the second
        // stands the first down instead of failing on uk_bom_headers_active_parent. The BOM itself is
        // read only after the lock, so it reflects whatever the winner committed.
        UUID parentItemId = bomHeaderRepository.findParentItemIdByBomId(bomId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM", bomId));
        itemLookupService.lockItem(parentItemId);
        BomHeader candidate = findBomWithLines(bomId);
        if (expectedVersion != null) {
            ensureVersion(candidate, expectedVersion);
        }
        ensureDraft(candidate);
        if (candidate.getLines().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.DOCUMENT_HAS_NO_LINES,
                    "Cannot activate BOM without component lines");
        }
        if (candidate.getEffectiveFrom() != null && candidate.getEffectiveFrom().isAfter(Instant.now())) {
            throw ExceptionFactory.custom(BusinessErrorCode.EFFECTIVE_DATE_FUTURE,
                    "BOM is effective from " + candidate.getEffectiveFrom() + "; it cannot be activated before then");
        }
        ensureSourcesActive(candidate);
        ensureNoCircularReference(candidate);

        bomHeaderRepository.findByCompanyCompanyIdAndParentItemItemIdAndStatus(
                        candidate.getCompany().getCompanyId(),
                        candidate.getParentItem().getItemId(),
                        BomStatus.ACTIVE)
                .filter(active -> !active.getBomId().equals(candidate.getBomId()))
                .ifPresent(active -> {
                    active.deactivate();
                    bomHeaderRepository.saveAndFlush(active);
                });

        candidate.activate();
        return mapper.toResponse(bomHeaderRepository.saveAndFlush(candidate));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)")
    public BomTreeNodeResponse getBomTree(UUID bomId) {
        BomHeader bom = findBomWithLines(bomId);
        return buildTreeRoot(bom, new LinkedHashSet<>());
    }

    private BomResponse createDraft(UUID companyId, BomDraftSaveRequest request, String bomCode) {
        Item parentItem = itemLookupService.getActiveItem(request.parentItemId());
        ensureParentItemAllowed(parentItem);
        ensureItemBelongsToCompany(parentItem, companyId);
        ensureCompanyActive(parentItem);
        List<ResolvedLine> lines = resolveLines(companyId, parentItem, request);

        itemLookupService.lockItem(parentItem.getItemId());
        ensureBomCodeFree(companyId, bomCode, null);
        int versionNumber = nextVersionNumber(companyId, parentItem.getItemId());
        BomHeader bom = BomHeader.builder()
                .company(parentItem.getCompany())
                .parentItem(parentItem)
                .revision(revisionFor(request.revision(), versionNumber, companyId, parentItem.getItemId(), null))
                .bomCode(bomCode)
                .versionNumber(versionNumber)
                .outputQuantity(request.outputQuantity())
                .effectiveFrom(request.effectiveFrom())
                .description(trimToNull(request.description()))
                .status(BomStatus.DRAFT)
                .build();
        for (ResolvedLine line : lines) {
            bom.getLines().add(line.toEntity(bom));
        }
        return mapper.toResponse(bomHeaderRepository.saveAndFlush(bom));
    }

    private BomResponse replaceDraft(UUID companyId, BomDraftSaveRequest request, String bomCode) {
        if (request.expectedVersion() == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("expectedVersion", "is required when bomId is given"));
        }
        BomHeader bom = bomHeaderRepository.findWithLinesByBomId(request.bomId())
                // Another company's BOM is answered exactly like a missing one — never confirmed.
                .filter(found -> found.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM", request.bomId()));
        ensureVersion(bom, request.expectedVersion());
        ensureDraft(bom);
        if (!bom.getParentItem().getItemId().equals(request.parentItemId())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("parentItemId", "cannot change; copy the BOM to make one for another product"));
        }
        List<ResolvedLine> lines = resolveLines(companyId, bom.getParentItem(), request);
        ensureBomCodeFree(companyId, bomCode, bom.getBomId());
        String revision = request.revision() == null
                ? bom.getRevision()
                : revisionFor(request.revision(), bom.getVersionNumber(), companyId,
                bom.getParentItem().getItemId(), bom);

        replaceLines(bom, lines);
        bom.setBomCode(bomCode);
        bom.setRevision(revision);
        bom.setOutputQuantity(request.outputQuantity());
        bom.setEffectiveFrom(request.effectiveFrom());
        bom.setDescription(trimToNull(request.description()));
        // Lines are an inverse collection: replacing only them would not dirty the header, and the
        // version would not move (CLAUDE.md §0.40 #2). Touching an audit field bumps it exactly once.
        bom.setUpdatedAt(Instant.now());
        return mapper.toResponse(bomHeaderRepository.saveAndFlush(bom));
    }

    /**
     * The checks that need nothing but the request — run first, before any lookup or lock (rule C9):
     * line numbers and components unique, no product as its own component, and a quotient that fits.
     */
    private void checkLineShape(BomDraftSaveRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<Integer> lineNumbers = new HashSet<>();
        Set<UUID> components = new HashSet<>();
        for (int index = 0; index < request.lines().size(); index++) {
            BomDraftLineRequest line = request.lines().get(index);
            if (!lineNumbers.add(line.lineNo())) {
                errors.put("lines[" + index + "].lineNo", "is used by another line");
            }
            if (line.componentItemId().equals(request.parentItemId())) {
                errors.put("lines[" + index + "].componentItemId", "cannot be the product itself");
            } else if (!components.add(line.componentItemId())) {
                errors.put("lines[" + index + "].componentItemId", "is already a component of this BOM");
            }
            if (perUnit(line.componentQuantity(), request.outputQuantity()) == null) {
                errors.put("lines[" + index + "].componentQuantity",
                        "divided by outputQuantity needs more than 6 decimals; adjust either quantity");
            }
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
    }

    /**
     * Validates every requested line against the product before anything is written, and computes
     * {@code quantityPer = componentQuantity / outputQuantity}. A quotient that does not fit 6 decimals
     * is refused on the line instead of being rounded (FE Q2): silently storing a different number than
     * the one typed would make the form show something else after reload.
     */
    private List<ResolvedLine> resolveLines(UUID companyId, Item parentItem, BomDraftSaveRequest request) {
        List<ResolvedLine> resolved = new ArrayList<>(request.lines().size());
        for (BomDraftLineRequest line : request.lines()) {
            Item component = itemLookupService.getActiveItem(line.componentItemId());
            // G5 (FE A03): the new paths also refuse a finished good as a component — a product made
            // from another sellable product belongs to a WIP. The old line endpoints keep their rule.
            if (component.getType() == ItemType.FINISHED_GOOD) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "BOM component " + component.getCode() + " is a finished good; use a raw material, WIP or consumable");
            }
            BigDecimal quantityPer = perUnit(line.componentQuantity(), request.outputQuantity());
            validateComponent(companyId, parentItem, component, line.lineNo(), quantityPer, line.scrapRate());
            resolved.add(new ResolvedLine(component, line.lineNo(), quantityPer, line.scrapRate()));
        }
        return resolved;
    }

    /**
     * Replaces the draft's lines, keeping the id of every line whose component stays. Removed lines go
     * first and are flushed, then surviving lines park on negative line numbers and are flushed again:
     * {@code uk_bom_lines_bom_line_no} is checked row by row, so swapping two line numbers in place, or
     * reusing the number of a removed line, would otherwise collide inside one flush (§0.40 #1).
     */
    private void replaceLines(BomHeader bom, List<ResolvedLine> lines) {
        Map<UUID, ResolvedLine> requestedByComponent = new LinkedHashMap<>();
        lines.forEach(line -> requestedByComponent.put(line.component().getItemId(), line));

        Map<UUID, BomLine> kept = new HashMap<>();
        bom.getLines().removeIf(existing -> {
            UUID componentId = existing.getComponentItem().getItemId();
            if (requestedByComponent.containsKey(componentId)) {
                kept.put(componentId, existing);
                return false;
            }
            return true;
        });
        bomHeaderRepository.flush();

        int parking = -1;
        for (BomLine line : kept.values()) {
            line.setLineNo(parking--);
        }
        bomHeaderRepository.flush();

        for (ResolvedLine requested : requestedByComponent.values()) {
            BomLine existing = kept.get(requested.component().getItemId());
            if (existing == null) {
                bom.getLines().add(requested.toEntity(bom));
            } else {
                existing.setLineNo(requested.lineNo());
                existing.setQuantityPer(requested.quantityPer());
                existing.setScrapRate(requested.scrapRate());
            }
        }
    }

    /** {@code null} when the exact quotient needs more than {@link #QUANTITY_SCALE} decimals. */
    private BigDecimal perUnit(BigDecimal componentQuantity, BigDecimal outputQuantity) {
        try {
            BigDecimal stripped = componentQuantity.divide(outputQuantity).stripTrailingZeros();
            if (stripped.scale() > QUANTITY_SCALE || stripped.signum() <= 0) {
                return null;
            }
            return stripped.setScale(QUANTITY_SCALE);
        } catch (ArithmeticException nonTerminating) {
            return null;
        }
    }

    /** The old line endpoints' checks, minus the "line number / component taken" lookups. */
    private void validateComponent(UUID companyId, Item parentItem, Item component, Integer lineNo,
                                   BigDecimal quantityPer, BigDecimal scrapRate) {
        BomHeader probe = BomHeader.builder().parentItem(parentItem).company(parentItem.getCompany()).build();
        ensureItemBelongsToCompany(component, companyId);
        validateLineForBom(probe, component, lineNo, quantityPer, scrapRate);
    }

    /**
     * G5 (FE R03): what activation re-checks besides the cycle. The product and every component must
     * still be ACTIVE — a draft saved last month may point at an item deactivated since.
     */
    private void ensureSourcesActive(BomHeader candidate) {
        if (!candidate.getParentItem().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "BOM product " + candidate.getParentItem().getCode() + " is inactive");
        }
        for (BomLine line : candidate.getLines()) {
            if (!line.getComponentItem().isActive()) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "BOM component " + line.getComponentItem().getCode() + " is inactive");
            }
        }
    }

    private void ensureVersion(BomHeader bom, long expectedVersion) {
        if (!Objects.equals(bom.getVersion(), expectedVersion)) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "BOM was modified by another request; reload it");
        }
    }

    private void ensureBomCodeFree(UUID companyId, String bomCode, UUID ownBomId) {
        boolean taken = ownBomId == null
                ? bomHeaderRepository.existsByCompanyCompanyIdAndBomCodeIgnoreCase(companyId, bomCode)
                : bomHeaderRepository.existsBomCodeOnOtherBom(companyId, bomCode, ownBomId);
        if (taken) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "BOM code", bomCode);
        }
    }

    /** Caller holds the item lock — see {@link ItemLookupService#lockItem}. */
    private int nextVersionNumber(UUID companyId, UUID parentItemId) {
        return bomHeaderRepository.findMaxVersionNumber(companyId, parentItemId) + 1;
    }

    /**
     * The requested label, or the version number when none is sent. Either way it must be free in the
     * family ({@code uk_bom_headers_company_parent_revision}): a legacy revision "2" can already hold
     * the label a new version 2 would default to, and the caller then has to name one.
     */
    private String revisionFor(String requested, int versionNumber, UUID companyId, UUID parentItemId,
                               BomHeader own) {
        String revision = StringUtils.hasText(requested) ? normalizeRevision(requested) : String.valueOf(versionNumber);
        if (own != null && own.getRevision().equals(revision)) {
            return revision;
        }
        if (bomHeaderRepository.existsByCompanyCompanyIdAndParentItemItemIdAndRevision(companyId, parentItemId, revision)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "BOM revision", revision);
        }
        return revision;
    }

    /** Product code + revision, the V73 backfill rule; the version number is appended on a collision. */
    private String derivedBomCode(UUID companyId, Item parentItem, String revision, int versionNumber) {
        String code = (parentItem.getCode() + "-" + revision).toUpperCase(Locale.ROOT);
        return bomHeaderRepository.existsByCompanyCompanyIdAndBomCodeIgnoreCase(companyId, code)
                ? code + "-V" + versionNumber
                : code;
    }

    private String normalizeBomCode(String bomCode) {
        String normalized = bomCode == null ? "" : bomCode.trim().toUpperCase(Locale.ROOT);
        if (!BOM_CODE_PATTERN.matcher(normalized).matches()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of("bomCode", "may only contain A-Z, 0-9, dot, underscore or hyphen"));
        }
        return normalized;
    }

    private BomHeader findBomWithLines(UUID bomId) {
        return bomHeaderRepository.findWithLinesByBomId(bomId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM", bomId));
    }

    private BomLine findLine(UUID lineId) {
        return bomLineRepository.findById(lineId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "BOM line", lineId));
    }

    private void ensureParentItemAllowed(Item parentItem) {
        if (parentItem.getType() != ItemType.WIP && parentItem.getType() != ItemType.FINISHED_GOOD) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "BOM parent item must be WIP or FINISHED_GOOD");
        }
    }

    private void validateLineForBom(BomHeader bom,
                                    Item componentItem,
                                    Integer lineNo,
                                    BigDecimal quantityPer,
                                    BigDecimal scrapRate) {
        ensureItemBelongsToCompany(componentItem, bom.getCompany().getCompanyId());
        if (componentItem.getType() == ItemType.SERVICE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Service item cannot be used as a BOM component");
        }
        if (componentItem.getItemId().equals(bom.getParentItem().getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE,
                    "BOM parent item cannot be its own component");
        }
        if (lineNo == null || lineNo <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                    "BOM line number must be greater than zero");
        }
        if (quantityPer == null || quantityPer.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                    "BOM quantity per must be greater than zero");
        }
        if (scrapRate == null
                || scrapRate.compareTo(BigDecimal.ZERO) < 0
                || scrapRate.compareTo(SCRAP_RATE_UPPER_BOUND) >= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BUSINESS_RULE_VIOLATION,
                    "BOM scrap rate must be greater than or equal to 0 and less than 1");
        }
    }

    private void ensureDraft(BomHeader bom) {
        if (!bom.isDraft()) {
            // D7: document status conflict → STATE_CONFLICT (409), same criterion as
            // RoutingService.activate. OPERATION_NOT_ALLOWED (422) stays for master-data validation.
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only draft BOM can be changed");
        }
    }

    private void ensureItemBelongsToCompany(Item item, UUID companyId) {
        if (!item.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to the BOM company");
        }
    }

    private void ensureCompanyActive(Item item) {
        if (!item.getCompany().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Cannot create BOM under inactive company: " + item.getCompany().getCompanyId());
        }
    }

    private void ensureNoCircularReference(BomHeader candidate) {
        detectCycle(candidate.getParentItem().getItemId(), candidate, new LinkedHashSet<>());
    }

    private void detectCycle(UUID itemId, BomHeader candidate, Set<UUID> path) {
        if (!path.add(itemId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }

        BomHeader bom = resolveBomForItem(candidate.getCompany().getCompanyId(), itemId, candidate).orElse(null);
        if (bom != null) {
            for (BomLine line : bom.getLines()) {
                UUID componentItemId = line.getComponentItem().getItemId();
                if (path.contains(componentItemId)) {
                    throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
                }
                detectCycle(componentItemId, candidate, path);
            }
        }
        path.remove(itemId);
    }

    private Optional<BomHeader> resolveBomForItem(UUID companyId, UUID itemId, BomHeader candidate) {
        if (candidate.getParentItem().getItemId().equals(itemId)) {
            return Optional.of(candidate);
        }
        return bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, itemId, BomStatus.ACTIVE);
    }

    private BomTreeNodeResponse buildTreeRoot(BomHeader bom, Set<UUID> path) {
        Item rootItem = bom.getParentItem();
        return new BomTreeNodeResponse(
                rootItem.getItemId(),
                rootItem.getCode(),
                rootItem.getName(),
                bom.getBomId(),
                bom.getRevision(),
                bom.getStatus() == BomStatus.ACTIVE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                buildComponents(bom, path),
                bom.getOutputQuantity(),
                null);
    }

    private List<BomTreeNodeResponse> buildComponents(BomHeader bom, Set<UUID> path) {
        UUID parentItemId = bom.getParentItem().getItemId();
        if (!path.add(parentItemId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }

        List<BomTreeNodeResponse> children = bom.getLines().stream()
                .sorted(Comparator.comparing(BomLine::getLineNo))
                .map(line -> buildComponentNode(line, bom.getCompany().getCompanyId(), path))
                .toList();
        path.remove(parentItemId);
        return children;
    }

    private BomTreeNodeResponse buildComponentNode(BomLine line, UUID companyId, Set<UUID> path) {
        Item component = line.getComponentItem();
        BigDecimal componentQuantity = BomMapper.componentQuantity(line.getQuantityPer(), line.getBom().getOutputQuantity());
        Optional<BomHeader> activeChildBom = bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, component.getItemId(), BomStatus.ACTIVE);

        if (activeChildBom.isEmpty()) {
            return new BomTreeNodeResponse(
                    component.getItemId(),
                    component.getCode(),
                    component.getName(),
                    null,
                    null,
                    false,
                    line.getQuantityPer(),
                    line.getScrapRate(),
                    List.of(),
                    null,
                    componentQuantity);
        }

        BomHeader childBom = activeChildBom.get();
        if (path.contains(component.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.BOM_CIRCULAR_REFERENCE);
        }
        return new BomTreeNodeResponse(
                component.getItemId(),
                component.getCode(),
                component.getName(),
                childBom.getBomId(),
                childBom.getRevision(),
                true,
                line.getQuantityPer(),
                line.getScrapRate(),
                buildComponents(childBom, path),
                childBom.getOutputQuantity(),
                componentQuantity);
    }

    private record ResolvedLine(Item component, Integer lineNo, BigDecimal quantityPer, BigDecimal scrapRate) {
        BomLine toEntity(BomHeader bom) {
            return BomLine.builder()
                    .bom(bom)
                    .componentItem(component)
                    .lineNo(lineNo)
                    .quantityPer(quantityPer)
                    .scrapRate(scrapRate)
                    .build();
        }
    }

    private String normalizeRevision(String revision) {
        return revision.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
