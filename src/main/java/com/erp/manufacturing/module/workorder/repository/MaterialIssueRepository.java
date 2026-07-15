package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.MaterialIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, UUID> {

    Optional<MaterialIssue> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"workOrder", "lines", "lines.componentLine", "lines.reservation",
            "lines.item", "lines.warehouse", "lines.lot", "lines.stockMovement"})
    Optional<MaterialIssue> findWithLinesByIdempotencyKey(String idempotencyKey);

    Page<MaterialIssue> findByWorkOrderWorkOrderId(UUID workOrderId, Pageable pageable);
}
