package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchasingCommand;
import com.erp.manufacturing.module.purchasing.domain.PurchasingIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchasingIdempotencyRecordRepository extends JpaRepository<PurchasingIdempotencyRecord, UUID> {

    /** Scope matches {@code uk_purchasing_idempotency_key_command}. */
    Optional<PurchasingIdempotencyRecord> findByIdempotencyKeyAndCommand(String idempotencyKey, PurchasingCommand command);
}
