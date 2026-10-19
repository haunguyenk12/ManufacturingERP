package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.domain.WorkOrderIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkOrderIdempotencyRecordRepository extends JpaRepository<WorkOrderIdempotencyRecord, UUID> {

    /** Scope matches {@code uk_work_order_idempotency_key_command}. */
    Optional<WorkOrderIdempotencyRecord> findByIdempotencyKeyAndCommand(String idempotencyKey, WorkOrderCommand command);
}
