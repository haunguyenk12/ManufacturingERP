package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BomService {

    private static final BigDecimal SCRAP_RATE_UPPER_BOUND = BigDecimal.ONE;

    private final BomHeaderRepository bomHeaderRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemLookupService itemLookupService;
    private final BomMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.BOM_CREATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()")
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

        BomHeader bom = BomHeader.builder()
                .company(parentItem.getCompany())
                .parentItem(parentItem)
                .revision(revision)
                .description(trimToNull(request.description()))
                .status(BomStatus.DRAFT)
                .build();
        return mapper.toResponse(bomHeaderRepository.save(bom));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_BOM_READ', 'COMPANY', #companyId)")
    public PageResult<BomResponse> listBoms(UUID companyId, UUID parentItemId, BomStatus status, Pageable pageable) {
        return PageResult.from(bomHeaderRepository.search(companyId, parentItemId, status, pageable).map(mapper::toResponse));
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
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()")
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
    @Auditable(action = AuditAction.BOM_DELETED, entityType = "BomHeader", entityIdExpression = "bomId.toString()")
    public BomResponse deactivateBom(UUID bomId) {
        BomHeader bom = findBomWithLines(bomId);
        bom.deactivate();
        return mapper.toResponse(bomHeaderRepository.save(bom));
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()")
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
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomLine", entityIdExpression = "bomId.toString()")
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
    @Auditable(action = AuditAction.BOM_UPDATED, entityType = "BomLine")
    public void deleteLine(UUID lineId) {
        BomLine line = findLine(lineId);
        ensureDraft(line.getBom());
        bomLineRepository.delete(line);
    }

    @Transactional
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_MANAGE', #bomId)")
    @Auditable(action = AuditAction.BOM_ACTIVATED, entityType = "BomHeader", entityIdExpression = "bomId.toString()")
    public BomResponse activateBom(UUID bomId) {
        BomHeader candidate = findBomWithLines(bomId);
        ensureDraft(candidate);
        if (candidate.getLines().isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot activate BOM without component lines");
        }
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
        return mapper.toResponse(bomHeaderRepository.save(candidate));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@bomPermissionGuard.hasBomAccess(authentication, 'PERM_BOM_READ', #bomId)")
    public BomTreeNodeResponse getBomTree(UUID bomId) {
        BomHeader bom = findBomWithLines(bomId);
        return buildTreeRoot(bom, new LinkedHashSet<>());
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
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only draft BOM can be changed");
        }
    }

    private void ensureItemBelongsToCompany(Item item, UUID companyId) {
        if (!item.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item must belong to the BOM company");
        }
    }

    private void ensureCompanyActive(Item item) {
        if (!item.getCompany().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
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
                buildComponents(bom, path));
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
                    List.of());
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
                buildComponents(childBom, path));
    }

    private String normalizeRevision(String revision) {
        return revision.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
