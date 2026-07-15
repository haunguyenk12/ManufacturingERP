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
import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionStatus;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionType;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionRepository;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequisitionService {

    private static final String SOURCE_TYPE_MRP_SUGGESTION = "MRP_SUGGESTION";
    private static final String REFERENCE_TYPE_PURCHASE_REQUISITION = "PURCHASE_REQUISITION";

    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final SupplySuggestionRepository supplySuggestionRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final SupplierService supplierService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchasingMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_CREATED, entityType = "PurchaseRequisition", entityIdExpression = "purchaseRequisitionId.toString()")
    public PurchaseRequisitionResponse create(PurchaseRequisitionCreateRequest request) {
        PurchaseRequisition requisition = buildRequisition(
                request.companyId(),
                request.plantId(),
                request.warehouseId(),
                normalizeCode(request.requisitionNo(), "Requisition number"),
                request.neededByDate(),
                trimToNull(request.sourceType()),
                request.sourceId());
        for (PurchaseRequisitionLineRequest lineRequest : request.lines()) {
            requisition.getLines().add(buildLine(requisition, lineRequest));
        }
        return mapper.toResponse(purchaseRequisitionRepository.save(requisition), true);
    }

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasSuggestionAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', #suggestionId)")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_CREATED, entityType = "PurchaseRequisition", entityIdExpression = "purchaseRequisitionId.toString()")
    public PurchaseRequisitionResponse convertFromSuggestion(UUID suggestionId,
                                                             PurchaseRequisitionFromSuggestionRequest request) {
        SupplySuggestion suggestion = supplySuggestionRepository.findWithDetailsBySupplySuggestionId(suggestionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Supply suggestion", suggestionId));
        if (suggestion.getStatus() != SupplySuggestionStatus.APPROVED) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only APPROVED supply suggestions can be converted");
        }
        if (suggestion.getSuggestionType() != SupplySuggestionType.PURCHASE_REQUISITION) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only PURCHASE_REQUISITION suggestions can be converted to purchase requisitions");
        }

        UUID warehouseId = request.warehouseId() != null
                ? request.warehouseId()
                : suggestion.getWarehouse() == null ? null : suggestion.getWarehouse().getWarehouseId();
        if (warehouseId == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Warehouse is required when suggestion is not warehouse-specific");
        }

        Supplier supplier = request.supplierId() == null
                ? supplierService.findPreferredActiveSupplierForItem(suggestion.getItem().getItemId())
                : supplierService.findActiveSupplier(request.supplierId());
        if (supplier != null) {
            ensureSupplierBelongsToCompany(supplier, suggestion.getCompany());
        }
        PurchaseRequisition requisition = buildRequisition(
                suggestion.getCompany().getCompanyId(),
                suggestion.getPlant().getPlantId(),
                warehouseId,
                normalizeCode(request.requisitionNo(), "Requisition number"),
                suggestion.getNeededByDate(),
                SOURCE_TYPE_MRP_SUGGESTION,
                suggestion.getSupplySuggestionId());
        PurchaseRequisitionLine line = PurchaseRequisitionLine.builder()
                .purchaseRequisition(requisition)
                .item(suggestion.getItem())
                .supplier(supplier)
                .requestedQuantity(requirePositive(suggestion.getSuggestedQuantity(), "Suggested quantity"))
                .neededByDate(suggestion.getNeededByDate())
                .note(trimToNull(request.note()))
                .build();
        requisition.getLines().add(line);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(requisition);
        suggestion.markConverted(REFERENCE_TYPE_PURCHASE_REQUISITION, saved.getPurchaseRequisitionId());
        supplySuggestionRepository.save(suggestion);
        return mapper.toResponse(saved, true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PURCHASE_REQUISITION_READ', 'PLANT', #plantId)")
    public PageResult<PurchaseRequisitionResponse> list(UUID companyId,
                                                        UUID plantId,
                                                        UUID warehouseId,
                                                        PurchaseRequisitionStatus status,
                                                        Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        if (warehouseId != null) {
            ensureWarehouseBelongsToPlant(organizationLookupService.getActiveWarehouse(warehouseId), plant);
        }
        return PageResult.from(purchaseRequisitionRepository.search(companyId, plantId, warehouseId, status, pageable)
                .map(requisition -> mapper.toResponse(requisition, false)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@purchasingPermissionGuard.hasRequisitionAccess(authentication, 'PERM_PURCHASE_REQUISITION_READ', #requisitionId)")
    public PurchaseRequisitionResponse get(UUID requisitionId) {
        return mapper.toResponse(findRequisition(requisitionId), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasRequisitionAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', #requisitionId)")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_APPROVED, entityType = "PurchaseRequisition", entityIdExpression = "purchaseRequisitionId.toString()")
    public PurchaseRequisitionResponse approve(UUID requisitionId, PurchaseDecisionRequest request) {
        PurchaseRequisition requisition = findRequisition(requisitionId);
        ensureDraft(requisition);
        Map<UUID, BigDecimal> overrides = approvalOverrides(request);
        for (PurchaseRequisitionLine line : requisition.getLines()) {
            BigDecimal approved = overrides.getOrDefault(
                    line.getPurchaseRequisitionLineId(), line.getRequestedQuantity());
            if (approved.compareTo(line.getRequestedQuantity()) > 0) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Approved quantity cannot exceed requested quantity");
            }
            line.setApprovedQuantity(requirePositive(approved, "Approved quantity"));
        }
        requisition.approve(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(purchaseRequisitionRepository.save(requisition), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasRequisitionAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', #requisitionId)")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_REJECTED, entityType = "PurchaseRequisition", entityIdExpression = "purchaseRequisitionId.toString()")
    public PurchaseRequisitionResponse reject(UUID requisitionId, PurchaseDecisionRequest request) {
        PurchaseRequisition requisition = findRequisition(requisitionId);
        ensureDraft(requisition);
        requisition.reject(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(purchaseRequisitionRepository.save(requisition), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasRequisitionAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', #requisitionId)")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_CANCELLED, entityType = "PurchaseRequisition", entityIdExpression = "purchaseRequisitionId.toString()")
    public PurchaseRequisitionResponse cancel(UUID requisitionId, PurchaseDecisionRequest request) {
        PurchaseRequisition requisition = findRequisition(requisitionId);
        ensureDraft(requisition);
        requisition.cancel(trimToNull(request == null ? null : request.decisionNote()));
        return mapper.toResponse(purchaseRequisitionRepository.save(requisition), true);
    }

    @Transactional
    @PreAuthorize("@purchasingPermissionGuard.hasRequisitionAccess(authentication, 'PERM_PURCHASE_REQUISITION_MANAGE', #requisitionId)")
    @Auditable(action = AuditAction.PURCHASE_REQUISITION_CONVERTED, entityType = "PurchaseOrder", entityIdExpression = "purchaseOrderId.toString()")
    public PurchaseOrderResponse convertToPurchaseOrder(UUID requisitionId,
                                                        PurchaseRequisitionConvertToOrderRequest request) {
        PurchaseRequisition requisition = findRequisition(requisitionId);
        if (!requisition.isApproved()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only APPROVED purchase requisitions can be converted to purchase orders");
        }
        Supplier supplier = resolveSupplierForConversion(requisition, request.supplierId());
        PurchaseOrderResponse order = purchaseOrderService.createFromRequisition(requisition, supplier, request);
        requisition.markConverted();
        purchaseRequisitionRepository.save(requisition);
        return order;
    }

    private PurchaseRequisition buildRequisition(UUID companyId,
                                                 UUID plantId,
                                                 UUID warehouseId,
                                                 String requisitionNo,
                                                 LocalDate neededByDate,
                                                 String sourceType,
                                                 UUID sourceId) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        Warehouse warehouse = organizationLookupService.getActiveWarehouse(warehouseId);
        ensurePlantBelongsToCompany(plant, company);
        ensureWarehouseBelongsToPlant(warehouse, plant);
        if (purchaseRequisitionRepository.existsByCompanyCompanyIdAndRequisitionNo(companyId, requisitionNo)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Requisition number", requisitionNo);
        }
        return PurchaseRequisition.builder()
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .requisitionNo(requisitionNo)
                .neededByDate(neededByDate)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .build();
    }

    private PurchaseRequisitionLine buildLine(PurchaseRequisition requisition,
                                              PurchaseRequisitionLineRequest request) {
        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureItemBelongsToCompany(item, requisition.getCompany());
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierService.findActiveSupplier(request.supplierId());
            ensureSupplierBelongsToCompany(supplier, requisition.getCompany());
        }
        return PurchaseRequisitionLine.builder()
                .purchaseRequisition(requisition)
                .item(item)
                .supplier(supplier)
                .requestedQuantity(requirePositive(request.requestedQuantity(), "Requested quantity"))
                .neededByDate(request.neededByDate() == null ? requisition.getNeededByDate() : request.neededByDate())
                .note(trimToNull(request.note()))
                .build();
    }

    private PurchaseRequisition findRequisition(UUID requisitionId) {
        return purchaseRequisitionRepository.findWithDetailsByPurchaseRequisitionId(requisitionId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Purchase requisition", requisitionId));
    }

    private Map<UUID, BigDecimal> approvalOverrides(PurchaseDecisionRequest request) {
        if (request == null || request.approvedLines() == null || request.approvedLines().isEmpty()) {
            return Map.of();
        }
        return request.approvedLines().stream()
                .collect(Collectors.toMap(
                        PurchaseRequisitionLineApprovalRequest::purchaseRequisitionLineId,
                        PurchaseRequisitionLineApprovalRequest::approvedQuantity));
    }

    private Supplier resolveSupplierForConversion(PurchaseRequisition requisition, UUID requestedSupplierId) {
        if (requestedSupplierId != null) {
            return supplierService.findActiveSupplier(requestedSupplierId);
        }
        Set<UUID> supplierIds = requisition.getLines().stream()
                .map(PurchaseRequisitionLine::getSupplier)
                .filter(Objects::nonNull)
                .map(Supplier::getSupplierId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (supplierIds.isEmpty()) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Supplier is required to convert requisition to purchase order");
        }
        if (supplierIds.size() > 1) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Purchase requisition has multiple suppliers; choose one supplier in request");
        }
        return supplierService.findActiveSupplier(supplierIds.iterator().next());
    }

    private void ensureDraft(PurchaseRequisition requisition) {
        if (!requisition.isDraft()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only DRAFT purchase requisitions can be changed");
        }
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Plant must belong to company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Warehouse must belong to plant");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item must belong to company");
        }
    }

    private void ensureSupplierBelongsToCompany(Supplier supplier, Company company) {
        if (!supplier.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Supplier must belong to company");
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity, String fieldName) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    fieldName + " must be greater than zero");
        }
        return quantity;
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
