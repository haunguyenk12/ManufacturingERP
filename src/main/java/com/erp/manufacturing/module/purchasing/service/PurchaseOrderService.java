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
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final SupplierService supplierService;
    private final PurchasingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CREATED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse create(PurchaseOrderCreateRequest request) {
        PurchaseOrder order = buildOrder(
                request.companyId(),
                request.plantId(),
                request.warehouseId(),
                supplierService.findActiveSupplier(request.supplierId()),
                normalizeCode(request.purchaseOrderNo(), "Purchase order number"),
                request.orderDate(),
                request.expectedDate(),
                request.sourceRequisitionId() == null ? null : findRequisition(request.sourceRequisitionId()),
                trimToNull(request.note()));
        for (PurchaseOrderLineRequest lineRequest : request.lines()) {
            order.getLines().add(buildManualLine(order, lineRequest));
        }
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_ORDER_READ', 'PLANT', #plantId)")
    public PageResult<PurchaseOrderResponse> list(UUID companyId,
                                                  UUID plantId,
                                                  UUID warehouseId,
                                                  UUID supplierId,
                                                  PurchaseOrderStatus status,
                                                  Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        if (warehouseId != null) {
            ensureWarehouseBelongsToPlant(organizationLookupService.getActiveWarehouse(warehouseId), plant);
        }
        return PageResult.from(purchaseOrderRepository.search(companyId, plantId, warehouseId, supplierId, status, pageable)
                .map(order -> mapper.toResponse(order, false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_READ', #purchaseOrderId)")
    public PurchaseOrderResponse get(UUID purchaseOrderId) {
        return mapper.toResponse(findOrder(purchaseOrderId), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_SENT, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse send(UUID purchaseOrderId) {
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureDraft(order, "Only DRAFT purchase orders can be sent");
        order.send();
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasOrderAccess(authentication, 'PERM_PURCHASE_ORDER_MANAGE', #purchaseOrderId)")
    @Auditable(action = AuditAction.PURCHASE_ORDER_CANCELLED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PurchaseOrderResponse cancel(UUID purchaseOrderId) {
        PurchaseOrder order = findOrder(purchaseOrderId);
        ensureDraft(order, "Only DRAFT purchase orders can be cancelled");
        order.cancel();
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    @Transactional
    PurchaseOrderResponse createFromRequisition(PurchaseRequisition requisition,
                                                Supplier supplier,
                                                PurchaseRequisitionConvertToOrderRequest request) {
        PurchaseOrder order = buildOrder(
                requisition.getCompany().getCompanyId(),
                requisition.getPlant().getPlantId(),
                requisition.getWarehouse().getWarehouseId(),
                supplier,
                normalizeCode(request.purchaseOrderNo(), "Purchase order number"),
                request.orderDate(),
                request.expectedDate(),
                requisition,
                trimToNull(request.note()));
        for (PurchaseRequisitionLine requisitionLine : requisition.getLines()) {
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .purchaseRequisitionLine(requisitionLine)
                    .item(requisitionLine.getItem())
                    .orderedQuantity(requirePositive(requisitionLine.effectiveApprovedQuantity(), "Approved quantity"))
                    .receivedQuantity(BigDecimal.ZERO)
                    .expectedDate(request.expectedDate())
                    .build();
            order.getLines().add(line);
        }
        return mapper.toResponse(purchaseOrderRepository.save(order), true);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findOrder(UUID purchaseOrderId) {
        return purchaseOrderRepository.findWithDetailsByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase order", purchaseOrderId));
    }

    private PurchaseOrder buildOrder(UUID companyId,
                                     UUID plantId,
                                     UUID warehouseId,
                                     Supplier supplier,
                                     String purchaseOrderNo,
                                     LocalDate orderDate,
                                     LocalDate expectedDate,
                                     PurchaseRequisition sourceRequisition,
                                     String note) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        Warehouse warehouse = organizationLookupService.getActiveWarehouse(warehouseId);
        ensurePlantBelongsToCompany(plant, company);
        ensureWarehouseBelongsToPlant(warehouse, plant);
        ensureSupplierBelongsToCompany(supplier, company);
        if (purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(companyId, purchaseOrderNo)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Purchase order number", purchaseOrderNo);
        }
        if (orderDate.isAfter(expectedDate)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Order date cannot be after expected date");
        }
        return PurchaseOrder.builder()
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrderNo(purchaseOrderNo)
                .orderDate(orderDate)
                .expectedDate(expectedDate)
                .sourceRequisition(sourceRequisition)
                .note(note)
                .build();
    }

    private PurchaseOrderLine buildManualLine(PurchaseOrder order, PurchaseOrderLineRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureItemBelongsToCompany(item, order.getCompany());
        return PurchaseOrderLine.builder()
                .purchaseOrder(order)
                .purchaseRequisitionLine(null)
                .item(item)
                .orderedQuantity(requirePositive(request.orderedQuantity(), "Ordered quantity"))
                .receivedQuantity(BigDecimal.ZERO)
                .unitPrice(validateOptionalNonNegative(request.unitPrice(), "Unit price"))
                .currencyCode(normalizeCurrency(request.currencyCode()))
                .expectedDate(request.expectedDate() == null ? order.getExpectedDate() : request.expectedDate())
                .build();
    }

    private PurchaseRequisition findRequisition(UUID requisitionId) {
        return purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisitionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase requisition", requisitionId));
    }

    private void ensureDraft(PurchaseOrder order, String message) {
        if (!order.isDraft()) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT, message);
        }
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Plant must belong to company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Warehouse must belong to plant");
        }
    }

    private void ensureSupplierBelongsToCompany(Supplier supplier, Company company) {
        if (!supplier.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Supplier must belong to company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to company");
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
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
        if (!StringUtils.hasText(value)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
