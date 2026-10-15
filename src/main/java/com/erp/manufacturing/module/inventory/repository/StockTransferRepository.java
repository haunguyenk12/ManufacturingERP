package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Optional<StockTransfer> findByIdempotencyKey(String idempotencyKey);

    /**
     * Transfer detail: header labels plus every line with its item and lot, in one statement. Only one
     * collection ({@code lines}) is fetched, so there is no second bag (§0.27/§0.29/§0.45).
     */
    @Query("""
            select distinct t
            from StockTransfer t
            join fetch t.plant
            join fetch t.fromWarehouse
            join fetch t.toWarehouse
            left join fetch t.lines l
            left join fetch l.item
            left join fetch l.lot
            where t.transferId = :transferId
            """)
    Optional<StockTransfer> findDetailByTransferId(@Param("transferId") UUID transferId);

    /**
     * Transfer list of one plant, optionally narrowed to transfers touching one warehouse (either side)
     * and to a code fragment. Only to-one fetches, so pagination stays in SQL (rule C13).
     * {@code cast(:search as string)} is mandatory, not defensive ({@code CLAUDE.md §0.24}).
     */
    @Query(value = """
            select t
            from StockTransfer t
            join fetch t.fromWarehouse fw
            join fetch t.toWarehouse tw
            where t.plant.plantId = :plantId
              and (:warehouseId is null or fw.warehouseId = :warehouseId or tw.warehouseId = :warehouseId)
              and (cast(:search as string) is null
                   or lower(t.code) like lower(concat('%', cast(:search as string), '%')))
            """, countQuery = """
            select count(t)
            from StockTransfer t
            where t.plant.plantId = :plantId
              and (:warehouseId is null
                   or t.fromWarehouse.warehouseId = :warehouseId or t.toWarehouse.warehouseId = :warehouseId)
              and (cast(:search as string) is null
                   or lower(t.code) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<StockTransfer> search(@Param("plantId") UUID plantId,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("search") String search,
                               Pageable pageable);
}
