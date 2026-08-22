package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    boolean existsByPlantPlantIdAndCode(UUID plantId, String code);

    List<Warehouse> findByPlantPlantIdAndCodeIn(UUID plantId, Collection<String> codes);

    Page<Warehouse> findByPlantPlantId(UUID plantId, Pageable pageable);

    List<Warehouse> findByPlantPlantId(UUID plantId);

    List<Warehouse> findByPlantCompanyCompanyId(UUID companyId);
}
