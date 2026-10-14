package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.Customer;
import com.erp.manufacturing.module.sales.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    @EntityGraph(attributePaths = {"company"})
    Optional<Customer> findWithCompanyByCustomerId(UUID customerId);

    /**
     * {@code cast(:search as string)} is load-bearing, exactly as in {@code SupplierRepository.search}:
     * a {@code null} search term is bound without a JDBC type, Postgres then resolves
     * {@code '%' || ? || '%'} to the {@code bytea} overload and rejects the statement with
     * {@code function lower(bytea) does not exist} — a 500 on the plain unfiltered list
     * (CLAUDE.md §0.24). {@code CustomerRepositoryIT} keeps the cast there.
     */
    @EntityGraph(attributePaths = {"company"})
    @Query("""
            select c
            from Customer c
            where c.company.companyId = :companyId
              and (:status is null or c.status = :status)
              and (cast(:search as string) is null
                   or lower(c.code) like lower(concat('%', cast(:search as string), '%'))
                   or lower(c.name) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Customer> search(@Param("companyId") UUID companyId,
                          @Param("status") CustomerStatus status,
                          @Param("search") String search,
                          Pageable pageable);
}
