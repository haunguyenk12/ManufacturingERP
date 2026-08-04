package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link SupplierRepository#search} against a real Postgres Testcontainer.
 *
 * <p>Exists because of a defect a mocked repository can never see (rule {@code R7}): a {@code null}
 * keyword is bound without a JDBC type, so Postgres resolved {@code '%' || ? || '%'} to the
 * {@code bytea} overload of {@code ||} and rejected the whole statement with
 * {@code function lower(bytea) does not exist} — a 500 on the plain unfiltered supplier list. The fix
 * is the {@code cast(:keyword as string)} in the query; {@link #search_withoutAKeywordReturnsEverySupplierOfTheCompany}
 * is what keeps it there. The identical bug in {@code WorkOrderRepository.search} was reported from
 * production on 2026-08-04.
 *
 * <p>The keyword cases seed a supplier that must <em>not</em> match alongside the one that must, so a
 * query that returned everything could not pass as correct filtering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SupplierRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    SupplierRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void search_withoutAKeywordReturnsEverySupplierOfTheCompany() {
        Company company = persistCompany();
        Supplier first = persistSupplier(company, "SUP-A", "Alpha Metals");
        Supplier second = persistSupplier(company, "SUP-B", "Beta Plastics");
        persistSupplier(persistCompany(), "SUP-C", "Gamma Chemicals");   // another company

        List<Supplier> found = repository
                .search(company.getCompanyId(), null, null, pageRequest())
                .getContent();

        assertThat(found).extracting(Supplier::getSupplierId)
                .containsExactlyInAnyOrder(first.getSupplierId(), second.getSupplierId());
    }

    @Test
    void search_matchesTheSupplierCodeCaseInsensitively() {
        Company company = persistCompany();
        Supplier wanted = persistSupplier(company, "SUP-ALPHA", "Nothing In The Name");
        persistSupplier(company, "SUP-BETA", "Beta Plastics");

        List<Supplier> found = repository
                .search(company.getCompanyId(), null, "alpha", pageRequest())
                .getContent();

        assertThat(found).extracting(Supplier::getSupplierId)
                .containsExactly(wanted.getSupplierId());
    }

    /** The keyword box covers both columns; matching only the code would half-answer it. */
    @Test
    void search_alsoMatchesTheSupplierName() {
        Company company = persistCompany();
        Supplier wanted = persistSupplier(company, "SUP-1", "Alpha Metals");
        persistSupplier(company, "SUP-2", "Beta Plastics");

        List<Supplier> found = repository
                .search(company.getCompanyId(), null, "metals", pageRequest())
                .getContent();

        assertThat(found).extracting(Supplier::getSupplierId)
                .containsExactly(wanted.getSupplierId());
    }

    @Test
    void search_withoutAKeywordStillHonoursTheStatusFilter() {
        Company company = persistCompany();
        Supplier active = persistSupplier(company, "SUP-A", "Alpha Metals");
        persistSupplier(company, "SUP-B", "Beta Plastics", SupplierStatus.INACTIVE);

        List<Supplier> found = repository
                .search(company.getCompanyId(), SupplierStatus.ACTIVE, null, pageRequest())
                .getContent();

        assertThat(found).extracting(Supplier::getSupplierId)
                .containsExactly(active.getSupplierId());
    }

    private static PageRequest pageRequest() {
        return PageRequest.of(0, 20);
    }

    private Company persistCompany() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Company company = Company.builder().code("CO_" + suffix).name("Company " + suffix)
                .status(OrganizationStatus.ACTIVE).build();
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        return entityManager.persistFlushFind(company);
    }

    private Supplier persistSupplier(Company company, String code, String name) {
        return persistSupplier(company, code, name, SupplierStatus.ACTIVE);
    }

    private Supplier persistSupplier(Company company, String code, String name, SupplierStatus status) {
        Instant now = Instant.now();

        Supplier supplier = Supplier.builder().company(company).code(code).name(name)
                .status(status).build();
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        return entityManager.persistFlushFind(supplier);
    }
}
