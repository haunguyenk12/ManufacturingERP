package com.erp.manufacturing.module.workorder.repository;

import com.erp.manufacturing.module.workorder.domain.QualityDisposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QualityDispositionRepository extends JpaRepository<QualityDisposition, UUID> {

    List<QualityDisposition> findByReceiptReceiptId(UUID receiptId);
}
