package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.dto.*;
import com.erp.manufacturing.module.organization.mapper.OrganizationMapper;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final CompanyRepository companyRepository;
    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrganizationMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ORG_READ')")
    public PageResult<CompanyResponse> listCompanies(Pageable pageable) {
        return PageResult.from(companyRepository.findAll(pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_READ', 'COMPANY', #companyId)")
    public CompanyResponse getCompany(UUID companyId) {
        return mapper.toResponse(findCompany(companyId));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasPermission(authentication, 'PERM_ORG_MANAGE')")
    @Auditable(action = AuditAction.COMPANY_CREATED, entityType = "Company", entityIdExpression = "companyId.toString()")
    public CompanyResponse createCompany(CompanyCreateRequest request) {
        String code = normalizeCode(request.code());
        if (companyRepository.existsByCode(code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Company code", code);
        }

        Company company = Company.builder()
                .code(code)
                .name(request.name().trim())
                .status(OrganizationStatus.ACTIVE)
                .build();
        return mapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.COMPANY_UPDATED, entityType = "Company", entityIdExpression = "companyId.toString()")
    public CompanyResponse updateCompany(UUID companyId, CompanyUpdateRequest request) {
        Company company = findCompany(companyId);
        company.setName(request.name().trim());
        return mapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.COMPANY_DEACTIVATED, entityType = "Company", entityIdExpression = "companyId.toString()")
    public CompanyResponse deactivateCompany(UUID companyId) {
        Company company = findCompany(companyId);
        company.deactivate();
        return mapper.toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_READ', 'COMPANY', #companyId)")
    public PageResult<PlantResponse> listPlants(UUID companyId, Pageable pageable) {
        ensureCompanyExists(companyId);
        return PageResult.from(plantRepository.findByCompanyCompanyId(companyId, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_READ', 'PLANT', #plantId)")
    public PlantResponse getPlant(UUID plantId) {
        return mapper.toResponse(findPlant(plantId));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.PLANT_CREATED, entityType = "Plant", entityIdExpression = "plantId.toString()")
    public PlantResponse createPlant(UUID companyId, PlantCreateRequest request) {
        Company company = findCompany(companyId);
        if (!company.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot create plant under inactive company: " + companyId);
        }

        String code = normalizeCode(request.code());
        if (plantRepository.existsByCompanyCompanyIdAndCode(companyId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Plant code", code);
        }

        Plant plant = Plant.builder()
                .company(company)
                .code(code)
                .name(request.name().trim())
                .timezone(resolveTimezone(request.timezone()))
                .status(OrganizationStatus.ACTIVE)
                .build();
        return mapper.toResponse(plantRepository.save(plant));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.PLANT_UPDATED, entityType = "Plant", entityIdExpression = "plantId.toString()")
    public PlantResponse updatePlant(UUID plantId, PlantUpdateRequest request) {
        Plant plant = findPlant(plantId);
        plant.setName(request.name().trim());
        plant.setTimezone(resolveTimezone(request.timezone()));
        return mapper.toResponse(plantRepository.save(plant));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.PLANT_DEACTIVATED, entityType = "Plant", entityIdExpression = "plantId.toString()")
    public PlantResponse deactivatePlant(UUID plantId) {
        Plant plant = findPlant(plantId);
        plant.deactivate();
        return mapper.toResponse(plantRepository.save(plant));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_READ', 'PLANT', #plantId)")
    public PageResult<WarehouseResponse> listWarehouses(UUID plantId, Pageable pageable) {
        ensurePlantExists(plantId);
        return PageResult.from(warehouseRepository.findByPlantPlantId(plantId, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_READ', 'WAREHOUSE', #warehouseId)")
    public WarehouseResponse getWarehouse(UUID warehouseId) {
        return mapper.toResponse(findWarehouse(warehouseId));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.WAREHOUSE_CREATED, entityType = "Warehouse", entityIdExpression = "warehouseId.toString()")
    public WarehouseResponse createWarehouse(UUID plantId, WarehouseCreateRequest request) {
        Plant plant = findPlant(plantId);
        if (!plant.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot create warehouse under inactive plant: " + plantId);
        }

        String code = normalizeCode(request.code());
        if (warehouseRepository.existsByPlantPlantIdAndCode(plantId, code)) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Warehouse code", code);
        }

        Warehouse warehouse = Warehouse.builder()
                .plant(plant)
                .code(code)
                .name(request.name().trim())
                .type(request.type())
                .status(OrganizationStatus.ACTIVE)
                .build();
        return mapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'WAREHOUSE', #warehouseId)")
    @Auditable(action = AuditAction.WAREHOUSE_UPDATED, entityType = "Warehouse", entityIdExpression = "warehouseId.toString()")
    public WarehouseResponse updateWarehouse(UUID warehouseId, WarehouseUpdateRequest request) {
        Warehouse warehouse = findWarehouse(warehouseId);
        warehouse.setName(request.name().trim());
        warehouse.setType(request.type());
        return mapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_ORG_MANAGE', 'WAREHOUSE', #warehouseId)")
    @Auditable(action = AuditAction.WAREHOUSE_DEACTIVATED, entityType = "Warehouse", entityIdExpression = "warehouseId.toString()")
    public WarehouseResponse deactivateWarehouse(UUID warehouseId) {
        Warehouse warehouse = findWarehouse(warehouseId);
        warehouse.deactivate();
        return mapper.toResponse(warehouseRepository.save(warehouse));
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId));
    }

    private Plant findPlant(UUID plantId) {
        return plantRepository.findById(plantId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Plant", plantId));
    }

    private Warehouse findWarehouse(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
    }

    private void ensureCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Company", companyId);
        }
    }

    private void ensurePlantExists(UUID plantId) {
        if (!plantRepository.existsById(plantId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Plant", plantId);
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveTimezone(String timezone) {
        return StringUtils.hasText(timezone) ? timezone.trim() : Plant.DEFAULT_TIMEZONE;
    }
}
