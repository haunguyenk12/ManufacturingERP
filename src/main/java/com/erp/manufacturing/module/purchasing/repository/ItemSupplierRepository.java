package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.ItemSupplier;
import com.erp.manufacturing.module.purchasing.domain.ItemSupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ItemSupplierRepository extends JpaRepository<ItemSupplier, UUID> {

    boolean existsByItemItemIdAndSupplierSupplierId(UUID itemId, UUID supplierId);

    boolean existsByItemItemIdAndPreferredIsTrueAndStatus(UUID itemId, ItemSupplierStatus status);

    boolean existsByItemItemIdAndPreferredIsTrueAndStatusAndItemSupplierIdNot(
            UUID itemId, ItemSupplierStatus status, UUID itemSupplierId);

    @EntityGraph(attributePaths = {"item", "item.company", "supplier", "supplier.company"})
    Optional<ItemSupplier> findByItemItemIdAndPreferredIsTrueAndStatus(UUID itemId, ItemSupplierStatus status);

    @EntityGraph(attributePaths = {"item", "item.company", "supplier", "supplier.company"})
    Optional<ItemSupplier> findWithDetailsByItemSupplierId(UUID itemSupplierId);

    @EntityGraph(attributePaths = {"item", "item.company", "supplier", "supplier.company"})
    Page<ItemSupplier> findByItemItemId(UUID itemId, Pageable pageable);

    @EntityGraph(attributePaths = {"item", "item.company", "supplier", "supplier.company"})
    Optional<ItemSupplier> findByItemItemIdAndSupplierSupplierIdAndStatus(
            UUID itemId, UUID supplierId, ItemSupplierStatus status);
}
