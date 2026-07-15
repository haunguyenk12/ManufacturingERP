package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.dto.ItemCreateRequest;
import com.erp.manufacturing.module.inventory.dto.ItemResponse;
import com.erp.manufacturing.module.inventory.dto.ItemUpdateRequest;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CompanyRepository companyRepository;
    private final InventoryMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.ITEM_CREATED, entityType = "Item", entityIdExpression = "itemId.toString()")
    public ItemResponse createItem(UUID companyId, ItemCreateRequest request) {
        Company company = findCompany(companyId);
        if (!company.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot create item under inactive company: " + companyId);
        }

        String code = normalizeCode(request.code());
        if (itemRepository.existsByCompanyCompanyIdAndCode(companyId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Item code", code);
        }

        Item item = Item.builder()
                .company(company)
                .code(code)
                .name(request.name().trim())
                .type(request.type())
                .unit(normalizeUnit(request.unit()))
                .lotTracked(request.lotTracked())
                .status(ItemStatus.ACTIVE)
                .build();
        return mapper.toResponse(itemRepository.save(item));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_INVENTORY_READ', 'COMPANY', #companyId)")
    public PageResult<ItemResponse> listItems(UUID companyId, Pageable pageable) {
        ensureCompanyExists(companyId);
        return PageResult.from(itemRepository.findByCompanyCompanyId(companyId, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_INVENTORY_READ', #itemId)")
    public ItemResponse getItem(UUID itemId) {
        return mapper.toResponse(findItem(itemId));
    }

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_INVENTORY_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_UPDATED, entityType = "Item", entityIdExpression = "itemId.toString()")
    public ItemResponse updateItem(UUID itemId, ItemUpdateRequest request) {
        Item item = findItem(itemId);
        ensureActiveItem(item);
        item.setName(request.name().trim());
        item.setUnit(normalizeUnit(request.unit()));
        return mapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    @PreAuthorize("@inventoryPermissionGuard.hasItemAccess(authentication, 'PERM_INVENTORY_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_DEACTIVATED, entityType = "Item", entityIdExpression = "itemId.toString()")
    public ItemResponse deactivateItem(UUID itemId) {
        Item item = findItem(itemId);
        item.deactivate();
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
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot update inactive item: " + item.getItemId());
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUnit(String unit) {
        return unit.trim().toUpperCase(Locale.ROOT);
    }
}
