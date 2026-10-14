package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemResponse;
import com.erp.manufacturing.module.inventory.dto.ItemUpdateRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.service.UomLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CompanyRepository companyRepository;
    private final UomLookupService uomLookupService;
    private final InventoryMapper mapper;

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemCompanyAccess(authentication, 'PERM_ITEM_MANAGE', #companyId)")
    @Auditable(action = AuditAction.ITEM_CREATED, entityType = "Item", entityIdExpression = "itemId.toString()",
               companyId = "#result?.companyId()")
    public ItemResponse createItem(UUID companyId, ItemCreateRequest request) {
        Company company = findCompany(companyId);
        if (request.serialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial Tracking is deferred; use NON_TRACKED or LOT_TRACKED");
        }
        if (!company.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Cannot create item under inactive company: " + companyId);
        }

        String code = normalizeCode(request.code());
        if (itemRepository.existsByCompanyCompanyIdAndCode(companyId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Item code", code);
        }
        if (request.lotTracked() && request.serialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item cannot be both lot-tracked and serial-tracked");
        }

        Item item = Item.builder()
                .company(company)
                .code(code)
                .name(request.name().trim())
                .type(request.type())
                .unit(requireActiveUom(request.unit()))
                .lotTracked(request.lotTracked())
                .serialTracked(request.serialTracked())
                .status(ItemStatus.ACTIVE)
                .build();
        return mapper.toResponse(itemRepository.save(item));
    }

    /** G2-4: every filter is optional and applied in one query, before pagination. */
    @Transactional(readOnly = true)
    @PreAuthorize("@inventoryPermissionGuard.hasItemCompanyAccess(authentication, 'PERM_ITEM_READ', #companyId)")
    public PageResult<ItemResponse> listItems(UUID companyId, String search, ItemStatus status, ItemType type,
                                              Boolean lotTracked, Pageable pageable) {
        ensureCompanyExists(companyId);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return PageResult.from(itemRepository.search(companyId, normalizedSearch, status, type, lotTracked, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_ITEM_READ', #itemId)")
    public ItemResponse getItem(UUID itemId) {
        return mapper.toResponse(findItem(itemId));
    }

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_ITEM_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_UPDATED, entityType = "Item", entityIdExpression = "itemId.toString()",
               companyId = "#result?.companyId()")
    /**
     * {@code unit} stays in the request so clients that resend it keep working, but it can no longer
     * change (G2-4, NEXT_PHASE_PLAN.md 9.2 D5): quantities already posted against the item are in the old
     * unit and there is no conversion. An unchanged value is accepted without a UOM lookup, because
     * items created before units were validated may carry a code the UOM catalogue never had.
     */
    public ItemResponse updateItem(UUID itemId, ItemUpdateRequest request) {
        Item item = findItem(itemId);
        ensureActiveItem(item);
        if (!normalizeUnit(request.unit()).equals(item.getUnit())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item unit cannot be changed (" + item.getUnit() + "): " + itemId);
        }
        item.setName(request.name().trim());
        return mapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_ITEM_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_DEACTIVATED, entityType = "Item", entityIdExpression = "itemId.toString()",
               companyId = "#result?.companyId()")
    public ItemResponse deactivateItem(UUID itemId) {
        Item item = findItem(itemId);
        item.deactivate();
        return mapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_ITEM_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_ACTIVATED, entityType = "Item", entityIdExpression = "itemId.toString()",
               companyId = "#result?.companyId()")
    public ItemResponse activateItem(UUID itemId) {
        Item item = findItem(itemId);
        if (!item.getCompany().isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Cannot activate an item while its company is inactive: " + itemId);
        }
        item.activate();
        return mapper.toResponse(itemRepository.save(item));
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId));
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId);
        }
    }

    private Item findItem(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", itemId));
    }

    private void ensureActiveItem(Item item) {
        if (!item.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Cannot update inactive item: " + item.getItemId());
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUnit(String unit) {
        return unit.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * G2-4 (D5): a new item's unit must be a UOM code that exists and is ACTIVE — the first time
     * {@code items.unit} is tied to the UOM catalogue. The column stays a code string; no FK.
     */
    private String requireActiveUom(String rawUnit) {
        String unit = normalizeUnit(rawUnit);
        Uom uom = uomLookupService.findByCode(List.of(unit)).get(unit);
        if (uom == null) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "UOM code not found: " + unit);
        }
        if (!uom.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive UOM cannot be used for a new item: " + unit);
        }
        return unit;
    }
}
