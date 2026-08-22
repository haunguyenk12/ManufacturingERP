package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationLookupService {

    private final CompanyRepository companyRepository;
    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public OrganizationScopeResolution resolveScope(ScopeResourceType scopeType, UUID scopeId) {
        return switch (scopeType) {
            case COMPANY -> resolveCompany(scopeId);
            case PLANT -> resolvePlant(scopeId);
            case WAREHOUSE -> resolveWarehouse(scopeId);
        };
    }

    @Transactional(readOnly = true)
    public Company getActiveCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId));
        if (company.getStatus() != OrganizationStatus.ACTIVE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive company cannot be used: " + companyId);
        }
        return company;
    }

    @Transactional(readOnly = true)
    public Plant getActivePlant(UUID plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Plant", plantId));
        if (plant.getStatus() != OrganizationStatus.ACTIVE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive plant cannot be used: " + plantId);
        }
        return plant;
    }

    @Transactional(readOnly = true)
    public Warehouse getActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (warehouse.getStatus() != OrganizationStatus.ACTIVE
                || warehouse.getPlant().getStatus() != OrganizationStatus.ACTIVE) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive warehouse cannot be used: " + warehouseId);
        }
        return warehouse;
    }

    /**
     * Lấy warehouse đang ACTIVE và kiểm tra nó thuộc về đúng plant.
     * Dùng cho các nghiệp vụ cần validate warehouse scope (work order, manufacturing execution...).
     */
    @Transactional(readOnly = true)
    public Warehouse getActiveWarehouseInPlant(UUID warehouseId, UUID plantId) {
        Warehouse warehouse = getActiveWarehouse(warehouseId);
        if (!warehouse.getPlant().getPlantId().equals(plantId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Warehouse " + warehouseId + " does not belong to plant " + plantId);
        }
        return warehouse;
    }

    @Transactional(readOnly = true)
    public Map<String, Plant> findPlantsByCode(UUID companyId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return plantRepository.findByCompanyCompanyIdAndCodeIn(companyId, codes).stream()
                .collect(Collectors.toMap(Plant::getCode, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<String, Warehouse> findWarehousesByCode(UUID plantId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findByPlantPlantIdAndCodeIn(plantId, codes).stream()
                .collect(Collectors.toMap(Warehouse::getCode, Function.identity()));
    }

    private OrganizationScopeResolution resolveCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId));
        List<UUID> warehouseIds = warehouseRepository.findByPlantCompanyCompanyId(company.getCompanyId()).stream()
                .map(Warehouse::getWarehouseId)
                .toList();
        return new OrganizationScopeResolution(ScopeResourceType.COMPANY, companyId, company.getCompanyId(), warehouseIds);
    }

    private OrganizationScopeResolution resolvePlant(UUID plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Plant", plantId));
        List<UUID> warehouseIds = warehouseRepository.findByPlantPlantId(plantId).stream()
                .map(Warehouse::getWarehouseId)
                .toList();
        return new OrganizationScopeResolution(
                ScopeResourceType.PLANT,
                plantId,
                plant.getCompany().getCompanyId(),
                warehouseIds);
    }

    private OrganizationScopeResolution resolveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        return new OrganizationScopeResolution(
                ScopeResourceType.WAREHOUSE,
                warehouseId,
                warehouse.getPlant().getCompany().getCompanyId(),
                List.of(warehouseId));
    }
}
