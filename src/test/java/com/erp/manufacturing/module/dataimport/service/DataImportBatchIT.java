package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ResultCarryingException;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.config.DataImportProperties;
import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRunStatus;
import com.erp.manufacturing.module.dataimport.domain.ImportTargetType;
import com.erp.manufacturing.module.dataimport.dto.DataImportBatchResponse;
import com.erp.manufacturing.module.dataimport.mapper.DataImportMapper;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.dataimport.security.DataImportBatchPermissionGuard;
import com.erp.manufacturing.module.dataimport.spreadsheet.FixedTemplateReader;
import com.erp.manufacturing.module.dataimport.spreadsheet.TemplateWorkbooks;
import com.erp.manufacturing.module.dataimport.target.CustomerImportTargetHandler;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.dto.CustomerCreateRequest;
import com.erp.manufacturing.module.sales.mapper.CustomerMapper;
import com.erp.manufacturing.module.sales.repository.CustomerRepository;
import com.erp.manufacturing.module.sales.service.CustomerLookupService;
import com.erp.manufacturing.module.sales.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.eq;

/**
 * The fixed-template commit against a real database (G3, rule R7). Everything this class asserts is a
 * statement about commit/rollback, unique indexes and row locks — none of which a mock repository has.
 *
 * <p>The {@code @DataJpaTest} wrapping transaction is off ({@code NOT_SUPPORTED}): the commit and the
 * "mark INVALID" after it are separate transactions and must really commit, exactly as in production.
 * Every test uses its own company, so nothing needs rolling back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DataImportBatchService.class, DataImportBatchCommitter.class, FixedTemplateValidator.class,
        FixedTemplateReader.class, ImportTargetRegistry.class, CustomerImportTargetHandler.class,
        CustomerService.class, CustomerLookupService.class, CustomerMapper.class,
        OrganizationLookupService.class, IdempotencySupport.class, DataImportMapper.class,
        com.erp.manufacturing.module.dataimport.target.BomImportHandler.class,
        com.erp.manufacturing.module.bom.service.BomService.class,
        com.erp.manufacturing.module.bom.service.BomLookupService.class,
        com.erp.manufacturing.module.bom.mapper.BomMapper.class,
        com.erp.manufacturing.module.inventory.service.ItemLookupService.class,
        DataImportBatchIT.Config.class})
class DataImportBatchIT extends AbstractPostgresIntegrationTest {

    private static final AtomicReference<UUID> CURRENT_USER = new AtomicReference<>();

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return () -> Optional.ofNullable(CURRENT_USER.get());
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean
        DataImportProperties dataImportProperties() {
            return new DataImportProperties(5_000, 0, 0);
        }
    }

    @Autowired DataImportBatchService service;
    @Autowired ImportRunRepository runRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired JdbcTemplate jdbc;
    @SpyBean CustomerService customerService;
    @SpyBean com.erp.manufacturing.module.bom.service.BomService bomService;
    @MockBean DataImportBatchPermissionGuard batchGuard;

    private UUID owner;
    private Company company;

    @BeforeEach
    void setUp() {
        owner = jdbc.queryForObject("SELECT user_id FROM users WHERE username = 'admin'", UUID.class);
        CURRENT_USER.set(owner);
        company = companyRepository.save(Company.builder().code("G3IT-" + UUID.randomUUID())
                .name("G3 IT").status(OrganizationStatus.ACTIVE).build());
    }

    /** FE R02: validation stores a READY batch and its rows — and creates no customer at all. */
    @Test
    void upload_validFile_storesReadyBatch_andCreatesNoMaster() {
        DataImportBatchResponse batch = upload(file("C-1", "C-2"));

        assertThat(batch.status()).isEqualTo("READY");
        assertThat(batch.rowCount()).isEqualTo(2);
        assertThat(batch.version()).isZero();
        assertThat(customerCount()).isZero();
        // The response must carry what was stored, not the 100 ns in-memory stamp Postgres rounds away.
        assertThat(service.get(batch.id()).createdAt()).isEqualTo(batch.createdAt());
    }

    /** FE R03/A15 at its simplest: a code taken after READY ⇒ nothing created, batch INVALID with the reason. */
    @Test
    void commit_codeTakenAfterValidation_createsNothing_andBatchBecomesInvalid() {
        DataImportBatchResponse batch = upload(file("C-1", "C-2", "C-3"));
        customerService.create(company.getCompanyId(), new CustomerCreateRequest("C-3", "Other", null, null, null, null, null));

        assertThatThrownBy(() -> service.commit(batch.id(), batch.version(), null))
                .isInstanceOf(ResultCarryingException.class)
                .satisfies(ex -> {
                    ResultCarryingException failure = (ResultCarryingException) ex;
                    assertThat(failure.getErrorCode()).isEqualTo(BusinessErrorCode.IMPORT_REVALIDATION_FAILED);
                    DataImportBatchResponse invalid = (DataImportBatchResponse) failure.getResult();
                    assertThat(invalid.status()).isEqualTo("INVALID");
                    assertThat(invalid.createdIds()).isEmpty();
                    assertThat(invalid.rows().get(2).issues()).singleElement()
                            .satisfies(issue -> assertThat(issue.code()).isEqualTo("ALREADY_EXISTS"));
                });

        assertThat(customerCount()).isEqualTo(1);
        assertThat(status(batch.id())).isEqualTo(ImportRunStatus.INVALID);
    }

    /**
     * FE A18: the database fails on the LAST row after the first two were inserted. All of it rolls
     * back, the batch stays READY (the data was never the problem), and a retry creates exactly three.
     */
    @Test
    void commit_failureOnTheLastRow_rollsBackTheRowsAlreadyWritten_andARetryCreatesExactlyN() {
        DataImportBatchResponse batch = upload(file("C-1", "C-2", "C-3"));
        doAnswer(invocation -> {
            throw new DataIntegrityViolationException("injected failure on the last row");
        }).when(customerService).create(eq(company.getCompanyId()),
                argThat(request -> request != null && "C-3".equals(request.code())));

        assertThatThrownBy(() -> service.commit(batch.id(), batch.version(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(customerCount()).isZero();
        assertThat(status(batch.id())).isEqualTo(ImportRunStatus.READY);

        doCallRealMethod().when(customerService).create(any(), any());
        DataImportBatchResponse committed = service.commit(batch.id(), batch.version(), null);

        assertThat(committed.status()).isEqualTo("COMMITTED");
        assertThat(committed.createdIds()).hasSize(3);
        assertThat(customerCount()).isEqualTo(3);
        // FE A17: the receipt read back later is the very receipt the first response carried.
        DataImportBatchResponse reread = service.get(batch.id());
        assertThat(reread.committedAt()).isEqualTo(committed.committedAt());
        assertThat(reread.createdIds()).isEqualTo(committed.createdIds());
    }

    /** FE A15: two batches sharing a code, committed at once — one wins whole, the other creates nothing. */
    @Test
    void commit_twoBatchesSharingACode_concurrently_oneWinsWhole_theOtherRollsBackItsOtherRowsToo() throws Exception {
        for (int round = 0; round < 3; round++) {
            // The shared code is the LAST row on purpose: an earlier row's insert is auto-flushed by the
            // next row's duplicate check, so only a last-row conflict proves the explicit flush inside
            // the committer's try block is what turns the lost race into a clean INVALID.
            String shared = "S-" + round;
            DataImportBatchResponse first = upload(file("A-" + round, shared));
            DataImportBatchResponse second = upload(file("B-" + round, shared));

            List<Object> outcomes = race(() -> service.commit(first.id(), first.version(), null),
                    () -> service.commit(second.id(), second.version(), null));

            assertThat(outcomes).filteredOn(DataImportBatchResponse.class::isInstance).as("round %d", round).hasSize(1);
            assertThat(outcomes).filteredOn(ResultCarryingException.class::isInstance).as("round %d", round).hasSize(1);
            assertThat(customerCount()).as("round %d", round).isEqualTo(2L * (round + 1));
            assertThat(customerRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "A-" + round)
                    ^ customerRepository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "B-" + round))
                    .as("exactly one of the two private codes exists, round %d", round).isTrue();
        }
    }

    /** FE A16: the same batch committed twice at once yields one receipt, N masters — never 2N. */
    @Test
    void commit_sameBatchTwiceConcurrently_returnsOneReceipt_andCreatesEachRowOnce() throws Exception {
        DataImportBatchResponse batch = upload(file("C-1", "C-2"));

        List<Object> outcomes = race(() -> service.commit(batch.id(), batch.version(), "K-1"),
                () -> service.commit(batch.id(), batch.version(), "K-2"));

        assertThat(outcomes).allMatch(DataImportBatchResponse.class::isInstance);
        assertThat(((DataImportBatchResponse) outcomes.get(0)).createdIds())
                .isEqualTo(((DataImportBatchResponse) outcomes.get(1)).createdIds());
        assertThat(customerCount()).isEqualTo(2);
    }

    /**
     * History: only the caller's own batches; partner batches have no plant and must still appear —
     * which an implicit inner join on {@code r.plant} would silently prevent.
     */
    @Test
    void searchBatches_isOwnerScoped_keepsPlantlessPartnerBatches_andMatchesFilenameCaseInsensitively() {
        Company other = companyRepository.save(Company.builder().code("G3IT-O-" + UUID.randomUUID())
                .name("Other").status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("P-" + UUID.randomUUID())
                .name("Plant").timezone("Asia/Ho_Chi_Minh").status(OrganizationStatus.ACTIVE).build());
        UUID stranger = insertUser();

        ImportRun mineCustomers = batch(owner, company, null, ImportTargetType.CUSTOMER, "Khach-Hang.xlsx");
        ImportRun mineItems = batch(owner, company, plant, ImportTargetType.ITEM, "items.xlsx");
        batch(stranger, company, null, ImportTargetType.CUSTOMER, "khach-hang-cua-nguoi-khac.xlsx");
        batch(owner, other, null, ImportTargetType.CUSTOMER, "khach-hang-cong-ty-khac.xlsx");
        PageRequest page = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "importRunId"));

        assertThat(runRepository.searchBatches(owner, company.getCompanyId(), null, false, true, true, false,
                null, null, page)).extracting(ImportRun::getImportRunId).containsExactly(mineCustomers.getImportRunId());
        assertThat(runRepository.searchBatches(owner, company.getCompanyId(), plant.getPlantId(), true, true, true, false,
                null, "KHACH", page)).extracting(ImportRun::getImportRunId).containsExactly(mineCustomers.getImportRunId());
        assertThat(runRepository.searchBatches(owner, company.getCompanyId(), plant.getPlantId(), true, false, false, false,
                null, null, page)).extracting(ImportRun::getImportRunId).containsExactly(mineItems.getImportRunId());
        assertThat(runRepository.searchBatches(owner, company.getCompanyId(), plant.getPlantId(), true, true, true, false,
                ImportRunStatus.COMMITTED, null, page)).isEmpty();
    }

    /**
     * G5 FE A11/A16: a BOM workbook of two groups validates READY without creating a BOM; the commit
     * creates exactly two DRAFTs (one per group, one id per BOM in createdIds) — and when the LAST group
     * fails at the database, the first BOM is rolled back with it and the batch stays READY.
     */
    @Test
    void bomBatch_validatesWithoutWriting_commitCreatesOneDraftPerGroup_andAFailedLastGroupLeavesNothing() {
        String tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        for (String code : List.of("FG1-" + tag, "FG2-" + tag)) {
            jdbc.update("INSERT INTO items (item_id, company_id, code, name, type, unit) VALUES (gen_random_uuid(), ?, ?, ?,"
                    + " 'FINISHED_GOOD', 'PCS')", company.getCompanyId(), code, code);
        }
        jdbc.update("INSERT INTO items (item_id, company_id, code, name, type, unit) VALUES (gen_random_uuid(), ?, ?, ?,"
                + " 'RAW_MATERIAL', 'KG')", company.getCompanyId(), "RM-" + tag, "RM");
        byte[] file = TemplateWorkbooks.boms("en")
                .row(2, "BOM1-" + tag, "FG1-" + tag, "10", "PCS", "RM-" + tag, "25", "KG", "5", "Notes")
                .row(3, "BOM2-" + tag, "FG2-" + tag, "1", "PCS", "RM-" + tag, "2", "KG", "", "")
                .build();

        DataImportBatchResponse batch = service.upload(new MockMultipartFile("file", "bom.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file),
                DataImportKind.BOMS, company.getCompanyId(), null, null);
        assertThat(batch.status()).isEqualTo("READY");
        assertThat(batch.bomCount()).isEqualTo(2);
        assertThat(bomCount()).isZero();

        // The database fails while writing the LAST group, after the first BOM was already flushed.
        doAnswer(invocation -> {
            throw new DataIntegrityViolationException("injected failure on the last BOM");
        }).when(bomService).saveDraft(eq(company.getCompanyId()),
                argThat(request -> request != null && ("BOM2-" + tag).equals(request.bomCode())));
        assertThatThrownBy(() -> service.commit(batch.id(), batch.version(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(bomCount()).isZero();
        assertThat(status(batch.id())).isEqualTo(ImportRunStatus.READY);

        doCallRealMethod().when(bomService).saveDraft(any(), any());
        DataImportBatchResponse committed = service.commit(batch.id(), batch.version(), null);

        assertThat(committed.status()).isEqualTo("COMMITTED");
        assertThat(committed.createdIds()).hasSize(2);
        assertThat(bomCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bom_headers WHERE company_id = ? AND status = 'DRAFT'"
                + " AND output_quantity = 10 AND bom_code = ?", Long.class, company.getCompanyId(), "BOM1-" + tag))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT quantity_per FROM bom_lines l JOIN bom_headers b ON b.bom_id = l.bom_id"
                + " WHERE b.bom_code = ?", java.math.BigDecimal.class, "BOM1-" + tag)).isEqualByComparingTo("2.5");
    }

    private long bomCount() {
        return jdbc.queryForObject("SELECT count(*) FROM bom_headers WHERE company_id = ?", Long.class,
                company.getCompanyId());
    }

    private DataImportBatchResponse upload(byte[] bytes) {
        return service.upload(new MockMultipartFile("file", "customers.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes),
                DataImportKind.CUSTOMERS, company.getCompanyId(), null, null);
    }

    private static byte[] file(String... codes) {
        TemplateWorkbooks workbook = TemplateWorkbooks.partners("customers", "en");
        for (int index = 0; index < codes.length; index++) {
            workbook.row(index + 2, codes[index], "Customer " + codes[index]);
        }
        return workbook.build();
    }

    private long customerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM customers WHERE company_id = ?", Long.class,
                company.getCompanyId());
    }

    private ImportRunStatus status(UUID batchId) {
        return runRepository.findById(batchId).orElseThrow().getStatus();
    }

    private ImportRun batch(UUID batchOwner, Company batchCompany, Plant plant, ImportTargetType target, String name) {
        ImportRun run = ImportRun.builder()
                .kind(ImportRunKind.FIXED_TEMPLATE)
                .ownerUserId(batchOwner)
                .targetType(target)
                .company(batchCompany)
                .plant(plant)
                .originalFilename(name)
                .fileSizeBytes(1L)
                .fileSha256("a".repeat(64))
                .detectedHeaders(List.of())
                .status(ImportRunStatus.READY)
                .build();
        return runRepository.saveAndFlush(run);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, username, email, password, status) VALUES (?, ?, ?, 'x', 'ACTIVE')",
                id, "g3-" + id, id + "@g3.local");
        return id;
    }

    /** Starts both calls at the same instant; each outcome is the return value or what was thrown. */
    private List<Object> race(java.util.concurrent.Callable<Object> a, java.util.concurrent.Callable<Object> b)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (java.util.concurrent.Callable<Object> call : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    CURRENT_USER.set(owner);
                    start.await();
                    try {
                        return call.call();
                    } catch (AppException | DataIntegrityViolationException failure) {
                        return failure;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
