package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.context.RequestContext;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportRowResponse;
import com.erp.manufacturing.module.dataimport.dto.ImportRunResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.spreadsheet.RawSheet;
import com.erp.manufacturing.module.dataimport.spreadsheet.SpreadsheetReader;
import com.erp.manufacturing.module.dataimport.target.ImportFieldDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetDescriptor;
import com.erp.manufacturing.module.dataimport.target.ImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportRunService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportRunRepository runRepository;
    private final ImportRowRepository rowRepository;
    private final ImportProfileRepository profileRepository;
    private final SpreadsheetReader spreadsheetReader;
    private final ImportTargetRegistry registry;
    private final OrganizationLookupService organizationLookupService;
    private final ImportRowApplyService rowApplyService;
    private final IdempotencySupport idempotency;
    private final DataImportProperties properties;
    private final DataImportMapper mapper;
    private final AuditLogService auditLogService;

    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', 'COMPANY', #companyId)")
    @Auditable(action = AuditAction.IMPORT_RUN_CREATED, entityType = "ImportRun",
            entityIdExpression = "importRunId.toString()")
    public ImportRunResponse upload(MultipartFile file, ImportTargetType targetType, UUID profileId,
                                    UUID companyId, UUID plantId, UUID warehouseId) {
        validateFile(file);
        registry.handlerFor(targetType);
        Company company = organizationLookupService.getActiveCompany(companyId);
        Plant plant = plantId == null ? null : organizationLookupService.getActivePlant(plantId);
        Warehouse warehouse = warehouseId == null
                ? null : organizationLookupService.getActiveWarehouse(warehouseId);
        validateScope(company, plant, warehouse);
        ImportProfile profile = profileId == null ? null : findUsableProfile(profileId, targetType, companyId);

        byte[] bytes = readFile(file);
        int headerRow = profile == null ? 0 : profile.getHeaderRowIndex();
        int firstDataRow = profile == null ? 1 : profile.getFirstDataRowIndex();
        RawSheet sheet = spreadsheetReader.read(new java.io.ByteArrayInputStream(bytes),
                profile == null ? null : profile.getSheetName(), headerRow, firstDataRow, properties.maxRows());
        if (sheet.rows().isEmpty()) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Spreadsheet contains no data rows");
        }

        ImportRun run = ImportRun.builder()
                .targetType(targetType)
                .profile(profile)
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .originalFilename(cleanFilename(file.getOriginalFilename()))
                .fileSizeBytes((long) bytes.length)
                .fileSha256(sha256(bytes))
                .detectedHeaders(sheet.headers())
                .status(ImportRunStatus.PARSING)
                .build();
        run = runRepository.saveAndFlush(run);
        ImportRun savedRun = run;
        rowRepository.saveAll(sheet.rows().stream()
                .map(raw -> ImportRow.builder()
                        .importRun(savedRun)
                        .rowNumber(raw.rowNumber())
                        .rawCells(raw.cells())
                        .status(ImportRowStatus.PENDING)
                        .build())
                .toList());
        run.markParsed(Instant.now(), sheet.rows().size());
        run = runRepository.save(run);
        return mapper.toResponse(run);
    }

    @Transactional
    @PreAuthorize("@dataImportPermissionGuard.hasRunAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', #runId)")
    @Auditable(action = AuditAction.IMPORT_RUN_VALIDATED, entityType = "ImportRun",
            entityIdExpression = "importRunId.toString()")
    public ImportRunResponse validate(UUID runId, UUID profileId) {
        ImportRun run = findRun(runId);
        if (!run.canValidate()) {
            throw invalidState(run, "validated");
        }
        run.startValidation();
        runRepository.saveAndFlush(run);
        ImportProfile profile = findUsableProfile(
                profileId, run.getTargetType(), run.getCompany().getCompanyId());
        ImportTargetHandler handler = registry.handlerFor(run.getTargetType());
        ImportTargetDescriptor descriptor = handler.descriptor();
        Map<String, ColumnMapping> mappingByTarget = profile.getMappings().stream()
                .collect(Collectors.toMap(ColumnMapping::targetField, mapping -> mapping));
        List<ImportRow> rows = rowRepository.findByImportRunImportRunIdOrderByRowNumberAsc(runId);

        List<RowValidation> validations = new ArrayList<>(rows.size());
        List<Map<String, String>> batchCandidates = new ArrayList<>();
        List<Integer> candidateToValidation = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            ImportRow row = rows.get(rowIndex);
            Map<String, String> values = new LinkedHashMap<>();
            List<ImportCellError> errors = new ArrayList<>();
            for (ImportFieldDescriptor field : descriptor.fields()) {
                ColumnMapping mapping = mappingByTarget.get(field.name());
                String value = null;
                if (mapping != null) {
                    try {
                        value = mapping.extract(row.getRawCells().get(mapping.sourceHeader()));
                    } catch (RuntimeException exception) {
                        errors.add(ImportCellError.of(mapping.sourceHeader(), field.name(),
                                ImportErrorCode.INVALID_FORMAT,
                                "Transform failed for value '" + row.getRawCells().get(mapping.sourceHeader())
                                        + "': " + exception.getMessage()));
                    }
                }
                values.put(field.name(), value);
                if (errors.stream().noneMatch(error -> field.name().equals(error.targetField()))) {
                    ImportCellError fieldError = field.check(
                            mapping == null ? null : mapping.sourceHeader(), value);
                    if (fieldError != null) {
                        errors.add(fieldError);
                    }
                }
            }
            validations.add(new RowValidation(row, values, errors));
            if (errors.isEmpty()) {
                candidateToValidation.add(rowIndex);
                batchCandidates.add(values);
            }
        }

        Map<Integer, List<ImportCellError>> batchErrors = handler.validateBatch(
                run.getCompany().getCompanyId(), batchCandidates);
        batchErrors.forEach((candidateIndex, errors) -> {
            if (candidateIndex >= 0 && candidateIndex < candidateToValidation.size()) {
                validations.get(candidateToValidation.get(candidateIndex)).errors().addAll(errors);
            }
        });

        int valid = 0;
        for (RowValidation validation : validations) {
            if (validation.errors().isEmpty()) {
                validation.row().markValid(validation.values());
                valid++;
            } else {
                validation.row().markInvalid(validation.values(), List.copyOf(validation.errors()));
            }
        }
        rowRepository.saveAll(rows);
        run.setProfile(profile);
        run.markValidated(Instant.now(), rows.size(), valid, rows.size() - valid);
        return mapper.toResponse(runRepository.save(run));
    }

    @PreAuthorize("@dataImportPermissionGuard.hasRunAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', #runId)")
    public ImportRunResponse apply(UUID runId, String idempotencyKey) {
        String normalizedKey = StringUtils.hasText(idempotencyKey)
                ? idempotency.normalizeKey(idempotencyKey) : null;
        ApplyFingerprint payload = new ApplyFingerprint(runId);
        if (normalizedKey != null) {
            Optional<ImportRun> replay = runRepository.findByIdempotencyKey(normalizedKey);
            if (replay.isPresent()) {
                idempotency.ensureSamePayload(replay.get().getPayloadHash(), payload);
                return mapper.toResponse(replay.get());
            }
        }

        ImportRun run = findRun(runId);
        if (!run.canApply()) {
            throw invalidState(run, "applied");
        }
        List<UUID> validRowIds = rowRepository
                .findByImportRunImportRunIdAndStatusOrderByRowNumberAsc(runId, ImportRowStatus.VALID)
                .stream().map(ImportRow::getImportRowId).toList();
        if (validRowIds.isEmpty()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Import run has no valid rows to apply");
        }
        run.setIdempotencyKey(normalizedKey);
        run.setPayloadHash(normalizedKey == null ? null : idempotency.payloadHash(payload));
        run.startApply();
        try {
            run = runRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException conflict) {
            if (normalizedKey != null) {
                ImportRun replay = runRepository.findByIdempotencyKey(normalizedKey).orElseThrow(() -> conflict);
                idempotency.ensureSamePayload(replay.getPayloadHash(), payload);
                return mapper.toResponse(replay);
            }
            throw conflict;
        }

        int applied = 0;
        int failed = 0;
        for (UUID rowId : validRowIds) {
            try {
                rowApplyService.apply(rowId);
                applied++;
            } catch (RuntimeException exception) {
                failed++;
                rowApplyService.markFailed(rowId, rootMessage(exception));
            }
        }
        run.markApplied(Instant.now(), applied, failed);
        run = runRepository.save(run);
        auditApplyOutcome(run);
        return mapper.toResponse(run);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_DATA_IMPORT_READ', 'COMPANY', #companyId)")
    public PageResult<ImportRunResponse> list(UUID companyId, ImportTargetType targetType,
                                               ImportRunStatus status, Pageable pageable) {
        organizationLookupService.getActiveCompany(companyId);
        return PageResult.from(runRepository.search(companyId, targetType, status, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@dataImportPermissionGuard.hasRunAccess(authentication, 'PERM_DATA_IMPORT_READ', #runId)")
    public ImportRunResponse get(UUID runId) {
        return mapper.toResponse(findRun(runId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@dataImportPermissionGuard.hasRunAccess(authentication, 'PERM_DATA_IMPORT_READ', #runId)")
    public PageResult<ImportRowResponse> rows(UUID runId, ImportRowStatus status, Pageable pageable) {
        if (!runRepository.existsById(runId)) {
            throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Import run", runId);
        }
        return PageResult.from(rowRepository.search(runId, status, pageable).map(mapper::toResponse));
    }

    @Transactional
    @PreAuthorize("@dataImportPermissionGuard.hasRunAccess(authentication, 'PERM_DATA_IMPORT_EXECUTE', #runId)")
    public ImportRunResponse cancel(UUID runId) {
        ImportRun run = findRun(runId);
        if (!run.canCancel()) {
            throw invalidState(run, "cancelled");
        }
        run.cancel();
        return mapper.toResponse(runRepository.save(run));
    }

    private ImportRun findRun(UUID runId) {
        return runRepository.findWithDetailsByImportRunId(runId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Import run", runId));
    }

    private ImportProfile findUsableProfile(UUID profileId, ImportTargetType targetType, UUID companyId) {
        ImportProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Import profile", profileId));
        if (!profile.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Import profile is inactive: " + profileId);
        }
        if (profile.getTargetType() != targetType) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Import profile target does not match run target");
        }
        if (profile.getCompany() != null && !profile.getCompany().getCompanyId().equals(companyId)) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Import profile belongs to another company");
        }
        return profile;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "An .xlsx file is required");
        }
        String filename = cleanFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                || !XLSX_CONTENT_TYPE.equals(file.getContentType())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Only .xlsx files with content type " + XLSX_CONTENT_TYPE + " are supported");
        }
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Uploaded spreadsheet could not be read", exception);
        }
    }

    private void validateScope(Company company, Plant plant, Warehouse warehouse) {
        if (plant != null && !plant.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                    "Plant does not belong to company");
        }
        if (warehouse != null) {
            if (plant == null) {
                throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                        "plantId is required when warehouseId is supplied");
            }
            if (!warehouse.getPlant().getPlantId().equals(plant.getPlantId())) {
                throw ExceptionFactory.custom(ValidationErrorCode.INVALID_INPUT,
                        "Warehouse does not belong to plant");
            }
        }
    }

    private RuntimeException invalidState(ImportRun run, String action) {
        return ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                "Import run in status " + run.getStatus() + " cannot be " + action);
    }

    private String cleanFilename(String filename) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(
                filename == null || filename.isBlank() ? "upload.xlsx" : filename);
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getClass().getSimpleName() : current.getMessage();
    }

    private void auditApplyOutcome(ImportRun run) {
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return;
            }
            AuditAction action = run.getStatus() == ImportRunStatus.FAILED
                    ? AuditAction.IMPORT_RUN_FAILED : AuditAction.IMPORT_RUN_APPLIED;
            auditLogService.logEntity(
                    RequestContext.capture(attrs.getRequest()), action, "ImportRun",
                    run.getImportRunId(), run.getCode());
        } catch (RuntimeException ignored) {
            // Audit is asynchronous observability and must not change the import result.
        }
    }

    private record RowValidation(ImportRow row, Map<String, String> values,
                                 List<ImportCellError> errors) {
    }

    private record ApplyFingerprint(UUID importRunId) {
    }
}
