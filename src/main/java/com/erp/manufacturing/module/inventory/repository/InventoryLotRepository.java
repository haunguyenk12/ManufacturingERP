package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, UUID> {

    Optional<InventoryLot> findByItemItemIdAndLotCode(UUID itemId, String lotCode);
}
