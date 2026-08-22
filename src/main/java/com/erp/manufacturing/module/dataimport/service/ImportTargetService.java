package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.ImportTargetResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.spreadsheet.SpreadsheetTemplateWriter;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImportTargetService {

    private final ImportTargetRegistry registry;
    private final SpreadsheetTemplateWriter templateWriter;
    private final DataImportMapper mapper;

    @PreAuthorize("@dataImportPermissionGuard.hasTargetReadAccess(authentication, #companyId)")
    public List<ImportTargetResponse> list(UUID companyId) {
        return registry.descriptors().stream().map(mapper::toResponse).toList();
    }

    @PreAuthorize("@dataImportPermissionGuard.hasTargetReadAccess(authentication, #companyId)")
    public ImportTargetResponse get(ImportTargetType type, UUID companyId) {
        return mapper.toResponse(registry.descriptorFor(type));
    }

    @PreAuthorize("@dataImportPermissionGuard.hasTargetReadAccess(authentication, #companyId)")
    public byte[] template(ImportTargetType type, UUID companyId) {
        return templateWriter.write(registry.descriptorFor(type));
    }
}
