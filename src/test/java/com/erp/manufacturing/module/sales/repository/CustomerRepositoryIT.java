package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
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
 * Runs {@link CustomerRepository#search} against a real Postgres Testcontainer (G2-1).
 *
 * <p>Same reason {@code SupplierRepositoryIT} exists (rule {@code R7}): a {@code null} search term is
 * bound without a JDBC type, and without the {@code cast(:search as string)} Postgres resolves
 * {@code '%' || ? || '%'} to the {@code bytea} overload and rejects the whole statement — a 500 on the
 * plain unfiltered list that no mocked repository can see (CLAUDE.md §0.24). The {@code null}-search
 * cases are the ones that keep the cast in place.
 *
 * <p>Every keyword case seeds a customer that must <em>not</em> match, so a query returning everything
 * cannot pass as filtering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    CustomerRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void search_withoutASearchTermReturnsEveryCustomerOfTheCompanyOnly() {
        Company company = persistCompany();
        Customer first = persistCustomer(company, "CUS-A", "Alpha Retail");
        Customer second = persistCustomer(company, "CUS-B", "Beta Wholesale");
        persistCustomer(persistCompany(), "CUS-C", "Gamma Stores");   // another company

        List<Customer> found = repository
                .search(company.getCompanyId(), null, null, pageRequest())
                .getContent();

        assertThat(found).extracting(Customer::getCustomerId)
                .containsExactlyInAnyOrder(first.getCustomerId(), second.getCustomerId());
    }

    @Test
    void search_matchesTheCodeCaseInsensitively() {
        Company company = persistCompany();
        Customer wanted = persistCustomer(company, "CUS-ALPHA", "Nothing In The Name");
        persistCustomer(company, "CUS-BETA", "Beta Wholesale");

        List<Customer> found = repository
                .search(company.getCompanyId(), null, "alpha", pageRequest())
                .getContent();

        assertThat(found).extracting(Customer::getCustomerId)
                .containsExactly(wanted.getCustomerId());
    }

    @Test
    void search_alsoMatchesTheName() {
        Company company = persistCompany();
        Customer wanted = persistCustomer(company, "CUS-1", "Alpha Retail");
        persistCustomer(company, "CUS-2", "Beta Wholesale");

        List<Customer> found = repository
                .search(company.getCompanyId(), null, "RETAIL", pageRequest())
                .getContent();

        assertThat(found).extracting(Customer::getCustomerId)
                .containsExactly(wanted.getCustomerId());
    }

    @Test
    void search_withoutASearchTermStillHonoursTheStatusFilter() {
        Company company = persistCompany();
        Customer active = persistCustomer(company, "CUS-A", "Alpha Retail");
        Customer inactive = persistCustomer(company, "CUS-B", "Beta Wholesale", CustomerStatus.INACTIVE);

        assertThat(repository.search(company.getCompanyId(), CustomerStatus.ACTIVE, null, pageRequest())
                .getContent())
                .extracting(Customer::getCustomerId)
                .containsExactly(active.getCustomerId());
        assertThat(repository.search(company.getCompanyId(), CustomerStatus.INACTIVE, null, pageRequest())
                .getContent())
                .extracting(Customer::getCustomerId)
                .containsExactly(inactive.getCustomerId());
    }

    /** Both filters applied together, as the FE list sends them. */
    @Test
    void search_combinesTheSearchTermWithTheStatusFilter() {
        Company company = persistCompany();
        Customer wanted = persistCustomer(company, "CUS-ALPHA-1", "Alpha Retail");
        persistCustomer(company, "CUS-ALPHA-2", "Alpha Outlet", CustomerStatus.INACTIVE);
        persistCustomer(company, "CUS-BETA", "Beta Wholesale");

        List<Customer> found = repository
                .search(company.getCompanyId(), CustomerStatus.ACTIVE, "alpha", pageRequest())
                .getContent();

        assertThat(found).extracting(Customer::getCustomerId)
                .containsExactly(wanted.getCustomerId());
    }

    @Test
    void existsByCompanyAndCode_isScopedToTheCompanyAndCountsInactiveCustomers() {
        Company company = persistCompany();
        persistCustomer(company, "CUS-OLD", "Retired Customer", CustomerStatus.INACTIVE);

        assertThat(repository.existsByCompanyCompanyIdAndCode(company.getCompanyId(), "CUS-OLD")).isTrue();
        assertThat(repository.existsByCompanyCompanyIdAndCode(persistCompany().getCompanyId(), "CUS-OLD"))
                .isFalse();
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

    private Customer persistCustomer(Company company, String code, String name) {
        return persistCustomer(company, code, name, CustomerStatus.ACTIVE);
    }

    private Customer persistCustomer(Company company, String code, String name, CustomerStatus status) {
        Instant now = Instant.now();

        Customer customer = Customer.builder().company(company).code(code).name(name)
                .status(status).build();
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return entityManager.persistFlushFind(customer);
    }
}
