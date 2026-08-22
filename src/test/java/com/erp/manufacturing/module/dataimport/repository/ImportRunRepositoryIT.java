package com.erp.manufacturing.module.dataimport.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.dataimport.domain.ColumnMapping;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportProfile;
import com.erp.manufacturing.module.dataimport.domain.ImportProfileStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImportRunRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ImportRunRepository runRepository;
    @Autowired ImportRowRepository rowRepository;

    @Test
    void jsonbRoundTripAndPagedStatusQueryPreserveUnknownColumnsAndCellErrors() {
        Company company = persistCompany();
        ImportProfile profile = persistProfile(company);
        ImportRun run = persistRun(company, profile);
        persistRow(run, 2, ImportRowStatus.VALID, null);
        persistRow(run, 3, ImportRowStatus.ERROR, List.of(ImportCellError.of(
                "Mã vật tư", "code", ImportErrorCode.REQUIRED_MISSING, "Code is required")));
        entityManager.flush();
        entityManager.clear();

        ImportRun loaded = runRepository.findWithDetailsByImportRunId(run.getImportRunId()).orElseThrow();
        assertThat(loaded.getCode()).startsWith("IMP-");
        assertThat(loaded.getProfile().getMappings()).singleElement()
                .satisfies(mapping -> assertThat(mapping.transforms()).containsExactly("TRIM", "UPPER"));

        var errors = rowRepository.search(run.getImportRunId(), ImportRowStatus.ERROR, PageRequest.of(0, 20));
        assertThat(errors).hasSize(1);
        ImportRow error = errors.getContent().get(0);
        assertThat(error.getRawCells()).containsEntry("Cột lạ", "vẫn được lưu");
        assertThat(error.getErrors()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(ImportErrorCode.REQUIRED_MISSING);
            assertThat(problem.targetField()).isEqualTo("code");
        });
    }

    @Test
    void searchScopesRunsToCompanyAndTarget() {
        Company company = persistCompany();
        ImportProfile profile = persistProfile(company);
        ImportRun run = persistRun(company, profile);
        entityManager.flush();
        entityManager.clear();

        var page = runRepository.search(company.getCompanyId(), ImportTargetType.ITEM,
                ImportRunStatus.PARSED, PageRequest.of(0, 10));

        assertThat(page).extracting(ImportRun::getImportRunId).contains(run.getImportRunId());
    }

    private Company persistCompany() {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        Company company = Company.builder().code("IMP_" + suffix).name("Import " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        return entityManager.persistFlushFind(company);
    }

    private ImportProfile persistProfile(Company company) {
        Instant now = Instant.now();
        ImportProfile profile = ImportProfile.builder()
                .code("PROFILE_" + UUID.randomUUID())
                .name("Item profile")
                .targetType(ImportTargetType.ITEM)
                .company(company)
                .headerRowIndex(0)
                .firstDataRowIndex(1)
                .mappings(List.of(new ColumnMapping(
                        "Mã vật tư", "code", List.of("TRIM", "UPPER"), null)))
                .status(ImportProfileStatus.ACTIVE)
                .build();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return entityManager.persistFlushFind(profile);
    }

    private ImportRun persistRun(Company company, ImportProfile profile) {
        Instant now = Instant.now();
        ImportRun run = ImportRun.builder()
                .targetType(ImportTargetType.ITEM)
                .profile(profile)
                .company(company)
                .originalFilename("vật-tư.xlsx")
                .fileSizeBytes(128L)
                .fileSha256("a".repeat(64))
                .detectedHeaders(List.of("Mã vật tư", "Cột lạ"))
                .status(ImportRunStatus.PARSED)
                .totalRows(2)
                .parsedAt(now)
                .build();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return entityManager.persistFlushFind(run);
    }

    private void persistRow(ImportRun run, int rowNumber, ImportRowStatus status,
                            List<ImportCellError> errors) {
        entityManager.persist(ImportRow.builder()
                .importRun(run)
                .rowNumber(rowNumber)
                .rawCells(Map.of("Mã vật tư", rowNumber == 2 ? "VT-001" : "", "Cột lạ", "vẫn được lưu"))
                .mappedValues(Map.of("code", rowNumber == 2 ? "VT-001" : ""))
                .status(status)
                .errors(errors)
                .build());
    }
}
