package com.erp.manufacturing.module.sales.repository;

import com.erp.manufacturing.module.sales.domain.SalesOrderPriceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalesOrderPriceEventRepository extends JpaRepository<SalesOrderPriceEvent, UUID> {
}
