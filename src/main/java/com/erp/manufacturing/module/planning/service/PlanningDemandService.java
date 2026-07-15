package com.erp.manufacturing.module.planning.service;

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
import com.erp.manufacturing.module.planning.domain.PlanningDemand;
import com.erp.manufacturing.module.planning.domain.PlanningDemandStatus;
import com.erp.manufacturing.module.planning.domain.PlanningDemandType;
import com.erp.manufacturing.module.planning.dto.PlanningDemandCreateRequest;
import com.erp.manufacturing.module.planning.dto.PlanningDemandResponse;
import com.erp.manufacturing.module.planning.mapper.MrpPlanningMapper;
import com.erp.manufacturing.module.planning.repository.PlanningDemandRepository;
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
public class PlanningDemandService {

    private final PlanningDemandRepository planningDemandRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final MrpPlanningMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PLANNING_DEMAND_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PLANNING_DEMAND_CREATED, entityType = "PlanningDemand", entityIdExpression = "planningDemandId.toString()")
    public PlanningDemandResponse create(PlanningDemandCreateRequest request) {
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        Plant plant = organizationLookupService.getActivePlant(request.plantId());
        ensurePlantBelongsToCompany(plant, company);

        Item item = itemLookupService.getActiveItem(request.itemId());
        ensureItemBelongsToCompany(item, company);

        Warehouse warehouse = null;
        if (request.warehouseId() != null) {
            warehouse = organizationLookupService.getActiveWarehouse(request.warehouseId());
            ensureWarehouseBelongsToPlant(warehouse, plant);
        }

        PlanningDemand demand = PlanningDemand.builder()
                .company(company)
                .plant(plant)
                .item(item)
                .warehouse(warehouse)
                .demandType(request.demandType() == null ? PlanningDemandType.MANUAL : request.demandType())
                .requiredQuantity(requirePositive(request.requiredQuantity()))
                .dueDate(request.dueDate())
                .priority(request.priority() == null ? 100 : request.priority())
                .referenceType(trimToNull(request.referenceType()))
                .referenceId(trimToNull(request.referenceId()))
                .build();
        return mapper.toResponse(planningDemandRepository.save(demand));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PLANNING_DEMAND_READ', 'PLANT', #plantId)")
    public PageResult<PlanningDemandResponse> list(UUID companyId,
                                                   UUID plantId,
                                                   UUID warehouseId,
                                                   UUID itemId,
                                                   PlanningDemandStatus status,
                                                   Pageable pageable) {
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = organizationLookupService.getActivePlant(plantId);
        ensurePlantBelongsToCompany(plant, company);
        if (warehouseId != null) {
            ensureWarehouseBelongsToPlant(organizationLookupService.getActiveWarehouse(warehouseId), plant);
        }
        return PageResult.from(planningDemandRepository.search(companyId, plantId, warehouseId, itemId, status, pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@mrpPlanningPermissionGuard.hasDemandAccess(authentication, 'PERM_PLANNING_DEMAND_READ', #demandId)")
    public PlanningDemandResponse get(UUID demandId) {
        return mapper.toResponse(findDemand(demandId));
    }

    @Transactional
    @PreAuthorize("@mrpPlanningPermissionGuard.hasDemandAccess(authentication, 'PERM_PLANNING_DEMAND_MANAGE', #demandId)")
    @Auditable(action = AuditAction.PLANNING_DEMAND_CANCELLED, entityType = "PlanningDemand", entityIdExpression = "demandId.toString()")
    public PlanningDemandResponse cancel(UUID demandId) {
        PlanningDemand demand = findDemand(demandId);
        if (!demand.isOpen()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Only OPEN planning demands can be cancelled");
        }
        demand.cancel();
        return mapper.toResponse(planningDemandRepository.save(demand));
    }

    private PlanningDemand findDemand(UUID demandId) {
        return planningDemandRepository.findWithDetailsByPlanningDemandId(demandId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Planning demand", demandId));
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Plant must belong to the selected company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item must belong to the selected company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Warehouse must belong to the selected plant");
        }
    }

    private BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "Required quantity must be greater than zero");
        }
        return quantity;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
