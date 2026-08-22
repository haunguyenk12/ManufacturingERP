package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    Page<Item> findByCompanyCompanyId(UUID companyId, Pageable pageable);

    @Query("""
            select i
            from Item i
            where i.company.companyId = :companyId
              and (lower(i.code) like lower(concat('%', :search, '%'))
                   or lower(i.name) like lower(concat('%', :search, '%')))
            """)
    Page<Item> searchByCodeOrName(@Param("companyId") UUID companyId,
                                  @Param("search") String search,
                                  Pageable pageable);

    /**
     * Batch counterpart of {@link #existsByCompanyCompanyIdAndCode} — one query for a whole set of
     * codes instead of one per code (rule C14).
     *
     * <p>Added for the spreadsheet importer, which sees a few thousand codes at once and must answer
     * "which of these already exist?" without a round trip per row. Codes are compared exactly as
     * stored, so callers normalise (trim + upper) first, the same way {@code ItemService} does.
     */
    List<Item> findByCompanyCompanyIdAndCodeIn(UUID companyId, Collection<String> codes);
}
