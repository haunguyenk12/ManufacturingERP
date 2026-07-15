package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.PurchaseRequisitionLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseRequisitionLineRepository extends JpaRepository<PurchaseRequisitionLine, UUID> {
}
