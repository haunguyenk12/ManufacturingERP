package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.ItemSupplierRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ItemSupplierRepository itemSupplierRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final PurchasingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SUPPLIER_MANAGE', 'COMPANY', #request.companyId())")
    @Auditable(action = AuditAction.SUPPLIER_CREATED, entityType = "Supplier", entityIdExpression = "supplierId.toString()")
    public SupplierResponse create(SupplierCreateRequest request) {
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        String code = normalizeCode(request.code(), "Supplier code");
        if (supplierRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Supplier code", code);
        }
        Supplier supplier = Supplier.builder()
                .company(company)
                .code(code)
                .name(requireText(request.name(), "Supplier name"))
                .email(trimToNull(request.email()))
                .phone(trimToNull(request.phone()))
                .address(trimToNull(request.address()))
                .taxCode(trimToNull(request.taxCode()))
                .build();
        return mapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_SUPPLIER_READ', 'COMPANY', #companyId)")
    public PageResult<SupplierResponse> list(UUID companyId, SupplierStatus status, String keyword, Pageable pageable) {
        organizationLookupService.getActiveCompany(companyId);
        return PageResult.from(supplierRepository.search(companyId, status, trimToNull(keyword), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasSupplierAccess(authentication, 'PERM_SUPPLIER_READ', #supplierId)")
    public SupplierResponse get(UUID supplierId) {
        return mapper.toResponse(findSupplier(supplierId));
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasSupplierAccess(authentication, 'PERM_SUPPLIER_MANAGE', #supplierId)")
    @Auditable(action = AuditAction.SUPPLIER_UPDATED, entityType = "Supplier", entityIdExpression = "supplierId.toString()")
    public SupplierResponse update(UUID supplierId, SupplierUpdateRequest request) {
        Supplier supplier = findSupplier(supplierId);
        if (request.name() != null) {
            supplier.setName(requireText(request.name(), "Supplier name"));
        }
        if (request.email() != null) {
            supplier.setEmail(trimToNull(request.email()));
        }
        if (request.phone() != null) {
            supplier.setPhone(trimToNull(request.phone()));
        }
        if (request.address() != null) {
            supplier.setAddress(trimToNull(request.address()));
        }
        if (request.taxCode() != null) {
            supplier.setTaxCode(trimToNull(request.taxCode()));
        }
        return mapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasSupplierAccess(authentication, 'PERM_SUPPLIER_MANAGE', #supplierId)")
    @Auditable(action = AuditAction.SUPPLIER_DEACTIVATED, entityType = "Supplier", entityIdExpression = "supplierId.toString()")
    public SupplierResponse deactivate(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);
        supplier.deactivate();
        return mapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasItemAccess(authentication, 'PERM_SUPPLIER_MANAGE', #itemId)")
    @Auditable(action = AuditAction.ITEM_SUPPLIER_CREATED, entityType = "ItemSupplier", entityIdExpression = "itemSupplierId.toString()")
    public ItemSupplierResponse addItemSupplier(UUID itemId, ItemSupplierRequest request) {
        if (request.supplierId() == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Supplier is required");
        }
        Item item = itemLookupService.getActiveItem(itemId);
        Supplier supplier = findActiveSupplier(request.supplierId());
        ensureSameCompany(item, supplier);
        if (itemSupplierRepository.existsByItemItemIdAndSupplierSupplierId(itemId, supplier.getSupplierId())) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Item supplier", supplier.getCode());
        }
        boolean preferred = Boolean.TRUE.equals(request.preferred());
        ensurePreferredAllowed(itemId, preferred, null);
        ItemSupplier itemSupplier = ItemSupplier.builder()
                .item(item)
                .supplier(supplier)
                .supplierItemCode(trimToNull(request.supplierItemCode()))
                .leadTimeDays(defaultLeadTime(request.leadTimeDays()))
                .minimumOrderQuantity(validateOptionalPositive(request.minimumOrderQuantity(), "Minimum order quantity"))
                .unitPrice(validateOptionalNonNegative(request.unitPrice(), "Unit price"))
                .currencyCode(normalizeCurrency(request.currencyCode()))
                .preferred(preferred)
                .build();
        return mapper.toResponse(itemSupplierRepository.save(itemSupplier));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasItemAccess(authentication, 'PERM_SUPPLIER_READ', #itemId)")
    public PageResult<ItemSupplierResponse> listItemSuppliers(UUID itemId, Pageable pageable) {
        itemLookupService.getItem(itemId);
        return PageResult.from(itemSupplierRepository.findByItemItemId(itemId, pageable).map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasItemSupplierAccess(authentication, 'PERM_SUPPLIER_MANAGE', #itemSupplierId)")
    @Auditable(action = AuditAction.ITEM_SUPPLIER_UPDATED, entityType = "ItemSupplier", entityIdExpression = "itemSupplierId.toString()")
    public ItemSupplierResponse updateItemSupplier(UUID itemId, UUID itemSupplierId, ItemSupplierRequest request) {
        ItemSupplier itemSupplier = findItemSupplierForItem(itemId, itemSupplierId);
        if (request.supplierItemCode() != null) {
            itemSupplier.setSupplierItemCode(trimToNull(request.supplierItemCode()));
        }
        if (request.leadTimeDays() != null) {
            itemSupplier.setLeadTimeDays(defaultLeadTime(request.leadTimeDays()));
        }
        if (request.minimumOrderQuantity() != null) {
            itemSupplier.setMinimumOrderQuantity(validateOptionalPositive(
                    request.minimumOrderQuantity(), "Minimum order quantity"));
        }
        if (request.unitPrice() != null) {
            itemSupplier.setUnitPrice(validateOptionalNonNegative(request.unitPrice(), "Unit price"));
        }
        if (request.currencyCode() != null) {
            itemSupplier.setCurrencyCode(normalizeCurrency(request.currencyCode()));
        }
        if (request.preferred() != null) {
            ensurePreferredAllowed(itemId, request.preferred(), itemSupplierId);
            itemSupplier.setPreferred(request.preferred());
        }
        return mapper.toResponse(itemSupplierRepository.save(itemSupplier));
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasItemSupplierAccess(authentication, 'PERM_SUPPLIER_MANAGE', #itemSupplierId)")
    @Auditable(action = AuditAction.ITEM_SUPPLIER_UPDATED, entityType = "ItemSupplier", entityIdExpression = "itemSupplierId.toString()")
    public ItemSupplierResponse deactivateItemSupplier(UUID itemId, UUID itemSupplierId) {
        ItemSupplier itemSupplier = findItemSupplierForItem(itemId, itemSupplierId);
        itemSupplier.deactivate();
        return mapper.toResponse(itemSupplierRepository.save(itemSupplier));
    }

    @Transactional(readOnly = true)
    public Supplier findActiveSupplier(UUID supplierId) {
        Supplier supplier = findSupplier(supplierId);
        if (!supplier.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive supplier cannot be used: " + supplierId);
        }
        return supplier;
    }

    @Transactional(readOnly = true)
    public Supplier findPreferredActiveSupplierForItem(UUID itemId) {
        Supplier supplier = itemSupplierRepository.findByItemItemIdAndPreferredIsTrueAndStatus(
                        itemId, ItemSupplierStatus.ACTIVE)
                .map(ItemSupplier::getSupplier)
                .orElse(null);
        if (supplier != null && !supplier.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Preferred supplier is inactive for item: " + itemId);
        }
        return supplier;
    }

    Supplier findSupplier(UUID supplierId) {
        return supplierRepository.findWithCompanyBySupplierId(supplierId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Supplier", supplierId));
    }

    private ItemSupplier findItemSupplierForItem(UUID itemId, UUID itemSupplierId) {
        ItemSupplier itemSupplier = itemSupplierRepository.findWithDetailsByItemSupplierId(itemSupplierId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Item supplier", itemSupplierId));
        if (!itemSupplier.getItem().getItemId().equals(itemId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item supplier does not belong to item");
        }
        return itemSupplier;
    }

    private void ensureSameCompany(Item item, Supplier supplier) {
        if (!item.getCompany().getCompanyId().equals(supplier.getCompany().getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item and supplier must belong to the same company");
        }
    }

    private void ensurePreferredAllowed(UUID itemId, boolean preferred, UUID currentItemSupplierId) {
        if (!preferred) {
            return;
        }
        boolean exists = currentItemSupplierId == null
                ? itemSupplierRepository.existsByItemItemIdAndPreferredIsTrueAndStatus(itemId, ItemSupplierStatus.ACTIVE)
                : itemSupplierRepository.existsByItemItemIdAndPreferredIsTrueAndStatusAndItemSupplierIdNot(
                        itemId, ItemSupplierStatus.ACTIVE, currentItemSupplierId);
        if (exists) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only one active preferred supplier is allowed for an item");
        }
    }

    private int defaultLeadTime(Integer leadTimeDays) {
        return leadTimeDays == null ? 0 : leadTimeDays;
    }

    private BigDecimal validateOptionalPositive(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal validateOptionalNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " cannot be negative");
        }
        return value;
    }

    private String normalizeCurrency(String currencyCode) {
        return StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase() : null;
    }

    private String normalizeCode(String value, String fieldName) {
        return requireText(value, fieldName).toUpperCase();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD, fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
