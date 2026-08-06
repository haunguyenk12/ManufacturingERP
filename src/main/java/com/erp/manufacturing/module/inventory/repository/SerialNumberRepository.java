package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.SerialNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, UUID> {

    Optional<SerialNumber> findByItemItemIdAndSerialCode(UUID itemId, String serialCode);
}
