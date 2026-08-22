package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.domain.TransformStep;
import com.erp.manufacturing.module.dataimport.dto.ColumnMappingRequest;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileCreateRequest;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportProfileUpdateRequest;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportFieldType;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.text.Normalizer;

@Service
@RequiredArgsConstructor
public class ImportProfileService {

    private final ImportProfileRepository profileRepository;
    private final OrganizationLookupService organizationLookupService;
    private final ImportTargetRegistry registry;
    private final DataImportMapper mapper;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', 'COMPANY', #request.companyId())")
    @Auditable(action = AuditAction.IMPORT_PROFILE_CREATED, entityType = "ImportProfile",
            entityIdExpression = "profileId.toString()")
    public ImportProfileResponse create(ImportProfileCreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (profileRepository.existsByCode(code)) {
            throw ExceptionFactory.alreadyExists(
                    ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Import profile code", code);
        }
        int headerRow = request.headerRowIndex() == null ? 0 : request.headerRowIndex();
        int firstDataRow = request.firstDataRowIndex() == null ? headerRow + 1 : request.firstDataRowIndex();
        List<ColumnMapping> mappings = toMappings(request.mappings());
        validateProfile(request.targetType(), headerRow, firstDataRow, mappings);
        Company company = organizationLookupService.getActiveCompany(request.companyId());
        ImportProfile profile = ImportProfile.builder()
                .code(code)
                .name(request.name().trim())
                .targetType(request.targetType())
                .company(company)
                .sheetName(trimToNull(request.sheetName()))
                .headerRowIndex(headerRow)
                .firstDataRowIndex(firstDataRow)
                .mappings(mappings)
                .status(ImportProfileStatus.ACTIVE)
                .build();
        return mapper.toResponse(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_DATA_IMPORT_READ', 'COMPANY', #companyId)")
    public PageResult<ImportProfileResponse> list(UUID companyId, ImportTargetType targetType,
                                                   ImportProfileStatus status, Pageable pageable) {
        organizationLookupService.getActiveCompany(companyId);
        return PageResult.from(profileRepository.search(companyId, targetType, status, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@dataImportPermissionGuard.hasProfileAccess(authentication, 'PERM_DATA_IMPORT_READ', #profileId)")
    public ImportProfileResponse get(UUID profileId) {
        return mapper.toResponse(findProfile(profileId));
    }

    @Transactional
    @PreAuthorize("@dataImportPermissionGuard.hasProfileAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', #profileId)")
    @Auditable(action = AuditAction.IMPORT_PROFILE_UPDATED, entityType = "ImportProfile",
            entityIdExpression = "profileId.toString()")
    public ImportProfileResponse update(UUID profileId, ImportProfileUpdateRequest request) {
        ImportProfile profile = findProfile(profileId);
        int headerRow = request.headerRowIndex() == null ? 0 : request.headerRowIndex();
        int firstDataRow = request.firstDataRowIndex() == null ? headerRow + 1 : request.firstDataRowIndex();
        List<ColumnMapping> mappings = toMappings(request.mappings());
        validateProfile(profile.getTargetType(), headerRow, firstDataRow, mappings);
        profile.setName(request.name().trim());
        profile.setSheetName(trimToNull(request.sheetName()));
        profile.setHeaderRowIndex(headerRow);
        profile.setFirstDataRowIndex(firstDataRow);
        profile.setMappings(mappings);
        if (request.status() != null) {
            profile.setStatus(request.status());
        }
        return mapper.toResponse(profileRepository.save(profile));
    }

    private void validateProfile(ImportTargetType targetType, int headerRow, int firstDataRow,
                                 List<ColumnMapping> mappings) {
        if (firstDataRow <= headerRow) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "firstDataRowIndex must be greater than headerRowIndex");
        }
        ImportTargetDescriptor descriptor = registry.descriptorFor(targetType);
        Set<String> targets = new HashSet<>();
        for (ColumnMapping mapping : mappings) {
            ImportFieldDescriptor field = descriptor.field(mapping.targetField())
                    .orElseThrow(() -> ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                            "Unknown target field '" + mapping.targetField() + "' for " + targetType));
            if (!targets.add(mapping.targetField())) {
                throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                        "Target field '" + mapping.targetField() + "' is mapped more than once");
            }
            for (String token : mapping.transforms()) {
                TransformStep step;
                try {
                    step = TransformStep.parse(token);
                } catch (RuntimeException exception) {
                    throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                            "Invalid transform '" + token + "' for field '" + mapping.targetField() + "'");
                }
                validateTransformType(step, field);
            }
            if (mapping.defaultValue() != null) {
                String transformedDefault;
                try {
                    transformedDefault = mapping.extract(null);
                } catch (RuntimeException exception) {
                    throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                            "Invalid default for field '" + mapping.targetField() + "': " + exception.getMessage());
                }
                if (field.check(mapping.sourceHeader(), transformedDefault) != null) {
                    throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                            "Default value is invalid for field '" + mapping.targetField() + "'");
                }
            }
        }
        List<String> missing = descriptor.requiredFields().stream()
                .map(ImportFieldDescriptor::name)
                .filter(name -> !targets.contains(name))
                .toList();
        if (!missing.isEmpty()) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Required target fields are not mapped: " + missing);
        }
    }

    private void validateTransformType(TransformStep step, ImportFieldDescriptor field) {
        boolean compatible = switch (step.type()) {
            case BOOLEAN_VN -> field.type() == ImportFieldType.BOOLEAN;
            case DECIMAL_COMMA, DECIMAL_DOT -> field.type() == ImportFieldType.DECIMAL;
            case EXCEL_DATE, DATE_FORMAT -> field.type() == ImportFieldType.DATE;
            case VALUE_DICT -> field.type() == ImportFieldType.ENUM || field.type() == ImportFieldType.STRING;
            default -> true;
        };
        if (!compatible) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Transform " + step.type() + " is not compatible with " + field.type()
                            + " field '" + field.name() + "'");
        }
    }

    private List<ColumnMapping> toMappings(List<ColumnMappingRequest> requests) {
        return requests.stream().map(mapping -> new ColumnMapping(
                normalizeHeader(mapping.sourceHeader()), mapping.targetField().trim(), mapping.transforms(),
                trimToNull(mapping.defaultValue()))).toList();
    }

    private String normalizeHeader(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replace('\u00a0', ' ')
                .replace('\u202f', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ImportProfile findProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Import profile", profileId));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
