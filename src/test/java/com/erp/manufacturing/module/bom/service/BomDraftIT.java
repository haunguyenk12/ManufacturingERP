package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.BomDraftLineRequest;
import com.erp.manufacturing.module.bom.dto.BomDraftSaveRequest;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import com.erp.manufacturing.module.bom.mapper.BomMapper;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G5 BOM drafts against real Postgres (rule R7): atomicity of the one-transaction save, the unique
 * line number that a naive re-save would trip, the family lock that keeps concurrent activations to a
 * single ACTIVE version, and the search query's {@code null} parameters.
 *
 * <p>The wrapping {@code @DataJpaTest} transaction is switched off: what is under test is what
 * survives a real commit or rollback.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BomService.class, BomMapper.class, ItemLookupService.class, BomDraftIT.AuditingConfig.class})
class BomDraftIT extends AbstractPostgresIntegrationTest {

    private static final int ROUNDS = 5;

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }
    }

    @Autowired BomService service;
    @Autowired BomHeaderRepository bomHeaderRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlatformTransactionManager transactionManager;

    /**
     * FE A04: the last line overflows {@code NUMERIC(19,6)} only when the INSERT reaches Postgres — after
     * the header and the first line were already flushed. The whole save rolls back: no header, no line.
     */
    @Test
    void saveDraft_failingOnTheLastLineAtTheDatabase_leavesNothingBehind() {
        Fixture f = fixture();

        assertThatThrownBy(() -> service.saveDraft(f.company.getCompanyId(), new BomDraftSaveRequest(null, null,
                "BOM-" + f.tag, f.product.getItemId(), null, new BigDecimal("0.000001"), null, null, List.of(
                new BomDraftLineRequest(1, f.raw.getItemId(), new BigDecimal("1"), BigDecimal.ZERO),
                new BomDraftLineRequest(2, f.wip.getItemId(), new BigDecimal("9999999999999"), BigDecimal.ZERO)))))
                .isInstanceOf(RuntimeException.class);

        assertThat(bomHeaderRepository.findMaxVersionNumber(f.company.getCompanyId(), f.product.getItemId())).isZero();
    }

    /**
     * A re-save that swaps two line numbers keeps both line ids: done in place in one flush, it would
     * hit {@code uk_bom_lines_bom_line_no} (the §0.40 trap) — which only a real database shows.
     */
    @Test
    void saveDraft_replaceSwappingLineNumbers_keepsTheLineIds() {
        Fixture f = fixture();
        BomResponse created = service.saveDraft(f.company.getCompanyId(), request(f, null, null,
                line(1, f.raw, "2"), line(2, f.wip, "3")));

        BomResponse replaced = service.saveDraft(f.company.getCompanyId(), request(f, created.bomId(), created.version(),
                line(1, f.wip, "3"), line(2, f.raw, "2")));

        assertThat(replaced.lines()).extracting(line -> line.componentItemCode())
                .containsExactly(f.wip.getCode(), f.raw.getCode());
        assertThat(replaced.lines().get(0).lineId()).isEqualTo(created.lines().get(1).lineId());
        assertThat(replaced.lines().get(1).lineId()).isEqualTo(created.lines().get(0).lineId());
        assertThat(replaced.version()).isEqualTo(created.version() + 1);
    }

    /**
     * FE A07: two drafts of one product activated at the same instant. With the family lock the second
     * stands the first down; without it one of them dies on {@code uk_bom_headers_active_parent}.
     * Either way never two ACTIVE — here, also never a failure.
     */
    @Test
    void concurrentActivationOfTwoVersions_bothSucceed_andExactlyOneIsActive() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = fixture();
            UUID first = service.saveDraft(f.company.getCompanyId(), request(f, null, null, line(1, f.raw, "1"))).bomId();
            UUID second = service.copyBom(first, new com.erp.manufacturing.module.bom.dto.BomCopyRequest(
                    "BOM-COPY-" + f.tag, null, null, null)).bomId();

            List<Throwable> failures = race(first, second);

            assertThat(failures).as("round %d", round).isEmpty();
            long active = new TransactionTemplate(transactionManager).execute(status -> bomHeaderRepository
                    .findByCompanyCompanyIdAndParentItemItemId(f.company.getCompanyId(), f.product.getItemId(),
                            PageRequest.of(0, 10))
                    .stream().filter(bom -> bom.getStatus() == BomStatus.ACTIVE).count());
            assertThat(active).as("round %d", round).isEqualTo(1);
        }
    }

    /** {@code search} and {@code latestOnly}: a null search must not turn {@code ||} into bytea (§0.24). */
    @Test
    void search_nullSearchAndLatestOnly_returnsTheHighestVersionPerProduct() {
        Fixture f = fixture();
        UUID first = service.saveDraft(f.company.getCompanyId(), request(f, null, null, line(1, f.raw, "1"))).bomId();
        UUID second = service.copyBom(first, new com.erp.manufacturing.module.bom.dto.BomCopyRequest(
                "BOM-COPY-" + f.tag, null, null, null)).bomId();

        List<UUID> all = new TransactionTemplate(transactionManager).execute(status -> bomHeaderRepository
                .search(f.company.getCompanyId(), null, null, null, false, PageRequest.of(0, 10))
                .map(BomHeader::getBomId).getContent());
        List<UUID> latest = new TransactionTemplate(transactionManager).execute(status -> bomHeaderRepository
                .search(f.company.getCompanyId(), null, null, null, true, PageRequest.of(0, 10))
                .map(BomHeader::getBomId).getContent());
        List<UUID> byCode = new TransactionTemplate(transactionManager).execute(status -> bomHeaderRepository
                .search(f.company.getCompanyId(), null, null, "copy-" + f.tag.toLowerCase(), false, PageRequest.of(0, 10))
                .map(BomHeader::getBomId).getContent());

        assertThat(all).containsExactlyInAnyOrder(first, second);
        assertThat(latest).containsExactly(second);
        assertThat(byCode).containsExactly(second);
    }

    private List<Throwable> race(UUID first, UUID second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (UUID bomId : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    service.activateBom(bomId, null);
                    return null;
                }));
            }
            start.countDown();
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private BomDraftSaveRequest request(Fixture f, UUID bomId, Long version, BomDraftLineRequest... lines) {
        return new BomDraftSaveRequest(bomId, version, "BOM-" + f.tag, f.product.getItemId(), null,
                BigDecimal.ONE, null, null, List.of(lines));
    }

    private BomDraftLineRequest line(int lineNo, Item component, String quantity) {
        return new BomDraftLineRequest(lineNo, component.getItemId(), new BigDecimal(quantity), BigDecimal.ZERO);
    }

    private record Fixture(String tag, Company company, Item product, Item raw, Item wip) {}

    private Fixture fixture() {
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Company company = companyRepository.save(Company.builder().code("G5B-" + tag).name("G5 BOM " + tag)
                .status(OrganizationStatus.ACTIVE).build());
        return new Fixture(tag, company,
                item(company, "FG-" + tag, ItemType.FINISHED_GOOD),
                item(company, "RM-" + tag, ItemType.RAW_MATERIAL),
                item(company, "WIP-" + tag, ItemType.WIP));
    }

    private Item item(Company company, String code, ItemType type) {
        return itemRepository.save(Item.builder().company(company).code(code).name(code).type(type).unit("EA")
                .status(ItemStatus.ACTIVE).lotTracked(false).build());
    }
}
