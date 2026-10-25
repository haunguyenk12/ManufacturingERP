package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.CommercialIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommercialIdempotencyRecordRepository extends JpaRepository<CommercialIdempotencyRecord, UUID> {

    /** Scope matches {@code uk_commercial_idempotency_key_command}. */
    Optional<CommercialIdempotencyRecord> findByIdempotencyKeyAndCommand(String idempotencyKey, CommercialCommand command);
}
