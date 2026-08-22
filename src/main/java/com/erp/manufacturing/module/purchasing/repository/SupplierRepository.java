package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.Supplier;
import com.erp.manufacturing.module.purchasing.domain.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    List<Supplier> findByCompanyCompanyIdAndCodeIn(UUID companyId, Collection<String> codes);

    @EntityGraph(attributePaths = {"company"})
    Optional<Supplier> findWithCompanyBySupplierId(UUID supplierId);

    /**
     * {@code cast(:keyword as string)} is load-bearing. A {@code null} keyword — the plain unfiltered
     * list, which is the common call — is bound without a JDBC type, so Postgres resolves
     * {@code '%' || ? || '%'} from the parameter alone, picks the {@code bytea} overload of {@code ||}
     * and rejects the statement at parse time with {@code function lower(bytea) does not exist}. The
     * {@code :keyword is null} short circuit does not save it: types are resolved before anything is
     * evaluated. Same defect, same fix as {@code WorkOrderRepository.search}.
     */
    @EntityGraph(attributePaths = {"company"})
    @Query("""
            select s
            from Supplier s
            where s.company.companyId = :companyId
              and (:status is null or s.status = :status)
              and (cast(:keyword as string) is null
                   or lower(s.code) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(s.name) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<Supplier> search(@Param("companyId") UUID companyId,
                          @Param("status") SupplierStatus status,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
