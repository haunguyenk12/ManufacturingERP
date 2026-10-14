package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.security.DataImportBatchPermissionGuard;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateReader;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
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

/**
 * Method security of the fixed-template path (G3) with the <em>real</em> {@link DataImportBatchPermissionGuard}
 * and only {@link PermissionGuard} mocked — the guard holds the logic under test (which pair, which
 * scope, owner first), so mocking it would test nothing (R3). Every deny names the exact permission
 * string it was refused on (R2).
 */
@SpringJUnitConfig(DataImportBatchMethodSecurityTest.Config.class)
class DataImportBatchMethodSecurityTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    @Autowired DataImportBatchService service;
    @Autowired PermissionGuard permissionGuard;
    @Autowired ImportRunRepository runRepository;
    @Autowired OrganizationLookupService organizationLookupService;
    @Autowired DataImportBatchCommitter committer;

    @BeforeEach
    void setUp() {
        reset(permissionGuard, runRepository, organizationLookupService, committer);
        UserPrincipal principal = new UserPrincipal(User.builder().userId(USER_ID).username("planner").build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadItems_deniedWithoutItemManageOnThePlant() {
        grant("PERM_ITEM_READ", "PLANT", PLANT_ID);

        assertThatThrownBy(() -> upload(DataImportKind.ITEMS, PLANT_ID)).isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ITEM_READ"), eq("PLANT"), eq(PLANT_ID));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ITEM_MANAGE"), eq("PLANT"), eq(PLANT_ID));
        verifyNoInteractions(organizationLookupService, runRepository);
    }

    @Test
    void uploadItems_allowedWithTheItemPairOnThePlant() {
        grant("PERM_ITEM_READ", "PLANT", PLANT_ID);
        grant("PERM_ITEM_MANAGE", "PLANT", PLANT_ID);

        // Past the guard: the service's own file check is what answers now.
        assertThatThrownBy(() -> upload(DataImportKind.ITEMS, PLANT_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.FILE_FORMAT));
    }

    @Test
    void uploadSuppliers_isJudgedOnTheSupplierPairAtCompanyScope() {
        grant("PERM_SUPPLIER_READ", "COMPANY", COMPANY_ID);

        assertThatThrownBy(() -> upload(DataImportKind.SUPPLIERS, null)).isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_SUPPLIER_MANAGE"), eq("COMPANY"), eq(COMPANY_ID));
    }

    @Test
    void uploadCustomers_allowedWithTheCustomerPair() {
        grant("PERM_CUSTOMER_READ", "COMPANY", COMPANY_ID);
        grant("PERM_CUSTOMER_MANAGE", "COMPANY", COMPANY_ID);

        assertThatThrownBy(() -> upload(DataImportKind.CUSTOMERS, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.FILE_FORMAT));
    }

    /** FE §1.4: READ of one catalogue and MANAGE of another never add up to an import. */
    @Test
    void uploadCustomers_deniedWhenReadAndManageComeFromDifferentCatalogues() {
        grant("PERM_SUPPLIER_READ", "COMPANY", COMPANY_ID);
        grant("PERM_CUSTOMER_MANAGE", "COMPANY", COMPANY_ID);

        assertThatThrownBy(() -> upload(DataImportKind.CUSTOMERS, null)).isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_CUSTOMER_READ"), eq("COMPANY"), eq(COMPANY_ID));
    }

    /** FE §1.4 / A20: another owner's batch id must not reveal that it exists — 404, not 403. */
    @Test
    void get_anotherOwnersBatch_isNotFound_withoutConsultingPermissions() {
        UUID batchId = UUID.randomUUID();
        when(runRepository.findByImportRunIdAndKind(batchId, ImportRunKind.FIXED_TEMPLATE))
                .thenReturn(Optional.of(batch(batchId, UUID.randomUUID(), ImportTargetType.CUSTOMER)));

        assertThatThrownBy(() -> service.get(batchId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(permissionGuard);
    }

    /** FE A21: the owner whose assignment was revoked cannot read — or replay — their own batch. */
    @Test
    void commit_ownBatchAfterThePairWasRevoked_isDenied_beforeAnyReceiptIsLookedUp() {
        UUID batchId = UUID.randomUUID();
        when(runRepository.findByImportRunIdAndKind(batchId, ImportRunKind.FIXED_TEMPLATE))
                .thenReturn(Optional.of(batch(batchId, USER_ID, ImportTargetType.ITEM)));
        grant("PERM_ITEM_READ", "PLANT", PLANT_ID);

        assertThatThrownBy(() -> service.commit(batchId, 0L, null)).isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_ITEM_MANAGE"), eq("PLANT"), eq(PLANT_ID));
        verifyNoInteractions(committer);
    }

    @Test
    void list_withoutKind_deniedWhenNoCataloguePairIsHeld() {
        assertThatThrownBy(() -> service.list(COMPANY_ID, null, null, null, null,
                org.springframework.data.domain.Pageable.ofSize(20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_SUPPLIER_READ"), eq("COMPANY"), eq(COMPANY_ID));
        verify(permissionGuard).hasResourceAccess(any(), eq("PERM_CUSTOMER_READ"), eq("COMPANY"), eq(COMPANY_ID));
        verifyNoInteractions(runRepository);
    }

    private void grant(String permission, String scopeType, UUID scopeId) {
        when(permissionGuard.hasResourceAccess(any(), eq(permission), eq(scopeType), eq(scopeId))).thenReturn(true);
    }

    private void upload(DataImportKind kind, UUID plantId) {
        service.upload(new MockMultipartFile("file", "data.csv", "text/csv", new byte[]{1}),
                kind, COMPANY_ID, plantId, null);
    }

    private static ImportRun batch(UUID batchId, UUID owner, ImportTargetType target) {
        Company company = Company.builder().companyId(COMPANY_ID).code("CO").name("Co")
                .status(OrganizationStatus.ACTIVE).build();
        return ImportRun.builder().importRunId(batchId).kind(ImportRunKind.FIXED_TEMPLATE).ownerUserId(owner)
                .targetType(target).company(company)
                .plant(target == ImportTargetType.ITEM
                        ? Plant.builder().plantId(PLANT_ID).company(company).code("P").name("P").build() : null)
                .originalFilename("f.xlsx").fileSizeBytes(1L).fileSha256("a").detectedHeaders(List.of())
                .status(ImportRunStatus.READY).build();
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean(name = "permissionGuard")
        PermissionGuard permissionGuard() {
            return mock(PermissionGuard.class);
        }

        @Bean
        ImportRunRepository runRepository() {
            return mock(ImportRunRepository.class);
        }

        @Bean
        OrganizationLookupService organizationLookupService() {
            return mock(OrganizationLookupService.class);
        }

        @Bean
        DataImportBatchCommitter committer() {
            return mock(DataImportBatchCommitter.class);
        }

        @Bean(name = "dataImportBatchGuard")
        DataImportBatchPermissionGuard dataImportBatchGuard(PermissionGuard permissionGuard,
                                                            ImportRunRepository runRepository) {
            return new DataImportBatchPermissionGuard(permissionGuard, runRepository);
        }

        @Bean
        DataImportBatchService dataImportBatchService(ImportRunRepository runRepository,
                                                      DataImportBatchCommitter committer,
                                                      DataImportBatchPermissionGuard guard,
                                                      OrganizationLookupService organizationLookupService) {
            AuditorAware<UUID> auditor = () -> Optional.of(USER_ID);
            return new DataImportBatchService(runRepository, mock(ImportRowRepository.class),
                    new FixedTemplateReader(), mock(FixedTemplateValidator.class), committer, guard,
                    organizationLookupService, new IdempotencySupport(new ObjectMapper().findAndRegisterModules()),
                    new DataImportProperties(5_000, 0, 0), new DataImportMapper(), auditor,
                    mock(jakarta.persistence.EntityManager.class));
        }
    }
}
