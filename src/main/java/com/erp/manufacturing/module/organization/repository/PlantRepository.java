package com.erp.manufacturing.module.organization.repository;

import com.erp.manufacturing.module.organization.domain.Plant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlantRepository extends JpaRepository<Plant, UUID> {

    boolean existsByCompanyCompanyIdAndCode(UUID companyId, String code);

    Page<Plant> findByCompanyCompanyId(UUID companyId, Pageable pageable);
}
