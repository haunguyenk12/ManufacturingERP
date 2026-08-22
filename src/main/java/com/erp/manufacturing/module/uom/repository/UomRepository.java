package com.erp.manufacturing.module.uom.repository;

import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.domain.UomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UomRepository extends JpaRepository<Uom, UUID> {

    boolean existsByCode(String code);

    List<Uom> findByCodeIn(Collection<String> codes);

    /**
     * {@code cast(:keyword as string)} is load-bearing, not decoration. A {@code null} keyword — the
     * plain unfiltered list, the common call for this endpoint — is bound without a JDBC type, so
     * Postgres resolves {@code '%' || ? || '%'} from the parameter alone, picks the {@code bytea}
     * overload of {@code ||}, and rejects the whole statement at parse time with
     * {@code function lower(bytea) does not exist} — a 500 on the plain list, not a filtering bug.
     * The {@code :keyword is null} short circuit does not save it: Postgres resolves types before it
     * evaluates anything. Same defect, same fix as {@code WorkOrderRepository.search} and
     * {@code SupplierRepository.search} (CLAUDE.md §0.24).
     */
    @Query("""
            select u
            from Uom u
            where (:status is null or u.status = :status)
              and (cast(:keyword as string) is null
                   or lower(u.code) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(u.name) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<Uom> search(@Param("status") UomStatus status,
                     @Param("keyword") String keyword,
                     Pageable pageable);
}
