package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, UUID> {

    Optional<InventoryLot> findByItemItemIdAndLotCode(UUID itemId, String lotCode);

    /** G7b (FE Q28): a purchase receipt may not reuse a lot code of the item, whatever its case. */
    boolean existsByItemItemIdAndLotCodeIgnoreCase(UUID itemId, String lotCode);
}
