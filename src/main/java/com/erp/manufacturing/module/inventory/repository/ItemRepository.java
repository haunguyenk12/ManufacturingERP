package com.erp.manufacturing.module.inventory.repository;

import com.erp.manufacturing.module.inventory.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    Page<Item> findByCompanyCompanyId(UUID companyId, Pageable pageable);
}
