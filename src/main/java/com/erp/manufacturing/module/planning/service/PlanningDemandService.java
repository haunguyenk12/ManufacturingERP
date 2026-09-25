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
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanningDemandService {

    /**
     * {@code referenceType} written on every demand generated from a confirmed sales order line
     * (F3). Owned by this module because it is this module's column; the sales module never
     * spells the value out.
     */
    public static final String REFERENCE_TYPE_SALES_ORDER_LINE = "SALES_ORDER_LINE";

    private final PlanningDemandRepository planningDemandRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ItemLookupService itemLookupService;
    private final MrpPlanningMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_PLANNING_DEMAND_MANAGE', 'PLANT', #request.plantId())")
    @Auditable(action = AuditAction.PLANNING_DEMAND_CREATED, entityType = "PlanningDemand", entityIdExpression = "planningDemandId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
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
    @Auditable(action = AuditAction.PLANNING_DEMAND_CANCELLED, entityType = "PlanningDemand", entityIdExpression = "demandId.toString()",
               companyId = "#result?.companyId()",
               plantId = "#result?.plantId()",
               warehouseId = "#result?.warehouseId()")
    public PlanningDemandResponse cancel(UUID demandId) {
        PlanningDemand demand = findDemand(demandId);
        if (!demand.isOpen()) {
            // Reads the document status, so 409 per the §5.3 criteria — not 422, which this module
            // keeps for master data and bad input (D11, debt #26).
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Only OPEN planning demands can be cancelled");
        }
        demand.cancel();
        return mapper.toResponse(planningDemandRepository.save(demand));
    }

    /**
     * Creates the independent demand behind one confirmed sales order line (spec §1: "Sales Order
     * được CONFIRMED và tạo independent demand theo từng dòng").
     *
     * <p>Intentionally <b>not</b> {@code @PreAuthorize}d, and intentionally taking plain arguments
     * rather than a sales entity: the caller
     * ({@code SalesOrderService.confirm}) has already been authorized against
     * {@code PERM_SALES_ORDER_MANAGE} on the same plant, and a sales user must not be forced to
     * also hold {@code PERM_PLANNING_DEMAND_MANAGE}. Same shape as
     * {@code PurchaseOrderService.createFromRequisition}. Cross-module entry point per rule
     * {@code C7} — sales never touches {@code PlanningDemandRepository}.
     */
    @Transactional
    public PlanningDemand createFromSalesOrderLine(Company company,
                                                   Plant plant,
                                                   Item item,
                                                   BigDecimal requiredQuantity,
                                                   LocalDate dueDate,
                                                   UUID salesOrderLineId) {
        PlanningDemand demand = PlanningDemand.builder()
                .company(company)
                .plant(plant)
                .item(item)
                .demandType(PlanningDemandType.SALES_ORDER)
                .requiredQuantity(requirePositive(requiredQuantity))
                .dueDate(dueDate)
                .referenceType(REFERENCE_TYPE_SALES_ORDER_LINE)
                .referenceId(salesOrderLineId.toString())
                .build();
        return planningDemandRepository.save(demand);
    }

    /**
     * Cancels the still-{@code OPEN} demand of the given sales order lines, so a cancelled sales
     * order stops driving MRP. Consumed demand ({@code CONSUMED}, already snapshotted into an MRP
     * run) is left alone — an immutable run must not change retroactively (spec §2.1).
     *
     * @return how many demands were cancelled
     */
    /**
     * Maps sales order line ids to the id of the still-{@code OPEN} planning demand each one produced,
     * in one query (rule {@code C14}).
     *
     * <p>Exists because {@code GET /sales-orders/planning-demands} listed lines the planner may pick
     * while {@code POST /planning-runs} resolves {@code demandLineIds} against
     * {@code PlanningDemand.planningDemandId} — an id the first endpoint never exposed, so the two
     * screens could not be connected (debt #21, found by {@code ProductionFlowE2EIT}). Lines whose
     * demand is already {@code CONSUMED} or {@code CANCELLED} are absent from the result, which is
     * correct: only {@code OPEN} demand may enter a run.
     *
     * <p>Like the two entry points above this is called by {@code sales} without its own
     * {@code @PreAuthorize} — the caller is already authorised on the same plant.
     */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findOpenDemandIdsBySalesOrderLineIds(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return Map.of();
        }
        return planningDemandRepository.findOpenByReference(
                        REFERENCE_TYPE_SALES_ORDER_LINE,
                        salesOrderLineIds.stream().map(UUID::toString).toList())
                .stream()
                .collect(Collectors.toMap(
                        demand -> UUID.fromString(demand.getReferenceId()),
                        PlanningDemand::getPlanningDemandId,
                        (first, second) -> first));
    }

    @Transactional
    public int cancelOpenDemandsForSalesOrderLines(Collection<UUID> salesOrderLineIds) {
        if (salesOrderLineIds.isEmpty()) {
            return 0;
        }
        List<PlanningDemand> demands = planningDemandRepository.findOpenByReference(
                REFERENCE_TYPE_SALES_ORDER_LINE,
                salesOrderLineIds.stream().map(UUID::toString).toList());
        demands.forEach(PlanningDemand::cancel);
        planningDemandRepository.saveAll(demands);
        return demands.size();
    }

    private PlanningDemand findDemand(UUID demandId) {
        return planningDemandRepository.findWithDetailsByPlanningDemandId(demandId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Planning demand", demandId));
    }

    private void ensurePlantBelongsToCompany(Plant plant, Company company) {
        if (!plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Plant must belong to the selected company");
        }
    }

    private void ensureItemBelongsToCompany(Item item, Company company) {
        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item must belong to the selected company");
        }
    }

    private void ensureWarehouseBelongsToPlant(Warehouse warehouse, Plant plant) {
        if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
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
