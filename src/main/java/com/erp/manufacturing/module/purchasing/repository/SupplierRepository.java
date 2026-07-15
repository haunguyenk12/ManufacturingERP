package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    @EntityGraph(attributePaths = {"company"})
    Optional<Supplier> findWithCompanyBySupplierId(UUID supplierId);

    @EntityGraph(attributePaths = {"company"})
    @Query("""
            select s
            from Supplier s
            where s.company.companyId = :companyId
              and (:status is null or s.status = :status)
              and (:keyword is null or lower(s.code) like lower(concat('%', :keyword, '%'))
                   or lower(s.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Supplier> search(@Param("companyId") UUID companyId,
                          @Param("status") SupplierStatus status,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
