package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.ProductionReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductionReceiptRepository extends JpaRepository<ProductionReceipt, UUID> {

    Optional<ProductionReceipt> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement"})
    Optional<ProductionReceipt> findWithLinesByIdempotencyKey(String idempotencyKey);

    Page<ProductionReceipt> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);
}
