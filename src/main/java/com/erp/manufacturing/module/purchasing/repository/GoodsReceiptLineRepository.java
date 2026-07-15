package com.erp.manufacturing.module.purchasing.repository;

import com.erp.manufacturing.module.purchasing.domain.GoodsReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, UUID> {
}
