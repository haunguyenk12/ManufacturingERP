package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportProfileRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.security.DataImportPermissionGuard;
import com.erp.manufacturing.module.dataimport.spreadsheet.SpreadsheetReader;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DataImportMethodSecurityTest.Config.class)
class DataImportMethodSecurityTest {

    @Autowired ImportRunService service;
    @Autowired PermissionGuard permissionGuard;
    @Autowired DataImportPermissionGuard dataImportPermissionGuard;
    @Autowired ImportRunRepository runRepository;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, dataImportPermissionGuard, runRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upload_deniedWithoutExecutePermissionAtCompanyScope() {
        UUID companyId = UUID.randomUUID();
        when(permissionGuard.hasResourceAccess(any(), eq("PERM_DATA_IMPORT_EXECUTE"),
                eq("COMPANY"), eq(companyId))).thenReturn(false);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", new byte[]{1}),
                ImportTargetType.ITEM, null, companyId, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_DATA_IMPORT_EXECUTE"),
                eq("COMPANY"), eq(companyId));
        verifyNoInteractions(runRepository);
    }

    @Test
    void validate_deniedBeforeRunIsLoaded() {
        UUID runId = UUID.randomUUID();
        when(dataImportPermissionGuard.hasRunAccess(any(), eq("PERM_DATA_IMPORT_EXECUTE"), eq(runId)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.validate(runId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verify(dataImportPermissionGuard)
                .hasRunAccess(any(), eq("PERM_DATA_IMPORT_EXECUTE"), eq(runId));
        verifyNoInteractions(runRepository);
    }

    @Test
    void get_allowedWithReadPermissionAndUsesTheRunGuard() {
        UUID runId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().companyId(companyId).code("CO").name("Company")
                .status(OrganizationStatus.ACTIVE).build();
        ImportRun run = ImportRun.builder().importRunId(runId).code("IMP-TEST")
                .targetType(ImportTargetType.ITEM).company(company).originalFilename("items.xlsx")
                .fileSizeBytes(10L).fileSha256("a".repeat(64)).detectedHeaders(List.of("Code"))
                .status(ImportRunStatus.PARSED).build();
        when(dataImportPermissionGuard.hasRunAccess(any(), eq("PERM_DATA_IMPORT_READ"), eq(runId)))
                .thenReturn(true);
        when(runRepository.findWithDetailsByImportRunId(runId)).thenReturn(Optional.of(run));

        assertThat(service.get(runId).importRunId()).isEqualTo(runId);
        verify(dataImportPermissionGuard).hasRunAccess(any(), eq("PERM_DATA_IMPORT_READ"), eq(runId));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        ImportRunService importRunService(ImportRunRepository runRepository,
                                          ImportRowRepository rowRepository,
                                          ImportProfileRepository profileRepository,
                                          ImportTargetRegistry registry,
                                          OrganizationLookupService organizationLookupService,
                                          ImportRowApplyService rowApplyService,
                                          AuditLogService auditLogService) {
            return new ImportRunService(runRepository, rowRepository, profileRepository,
                    new SpreadsheetReader(), registry, organizationLookupService, rowApplyService,
                    new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                    new DataImportProperties(5_000), new DataImportMapper(), auditLogService);
        }

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean(name = "dataImportPermissionGuard")
        DataImportPermissionGuard dataImportPermissionGuard() {
            return mock(DataImportPermissionGuard.class);
        }

        @Bean ImportRunRepository runRepository() { return mock(ImportRunRepository.class); }
        @Bean ImportRowRepository rowRepository() { return mock(ImportRowRepository.class); }
        @Bean ImportProfileRepository profileRepository() { return mock(ImportProfileRepository.class); }
        @Bean ImportTargetRegistry registry() { return mock(ImportTargetRegistry.class); }
        @Bean OrganizationLookupService organizationLookupService() { return mock(OrganizationLookupService.class); }
        @Bean ImportRowApplyService rowApplyService() { return mock(ImportRowApplyService.class); }
        @Bean AuditLogService auditLogService() { return mock(AuditLogService.class); }
    }
}
